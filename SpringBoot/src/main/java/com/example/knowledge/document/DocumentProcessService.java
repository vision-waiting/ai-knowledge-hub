package com.example.knowledge.document;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.entity.DocumentTag;
import com.example.knowledge.mapper.DocumentChunkMapper;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.mapper.DocumentTagMapper;
import com.example.knowledge.rag.EmbeddingService;
import com.example.knowledge.rag.VectorStore;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentProcessService {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessService.class);
    @org.springframework.beans.factory.annotation.Autowired
    private DocumentVersionActivation versionActivation;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentTagMapper tagMapper;
    private final List<DocumentParser> parsers;
    private final TextChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.knowledge.rag.AiService aiService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.knowledge.notifications.NotificationPublisher notificationPublisher;

    public DocumentProcessService(DocumentMapper documentMapper, DocumentChunkMapper chunkMapper,
                                  DocumentTagMapper tagMapper, List<DocumentParser> parsers, TextChunker chunker,
                                  EmbeddingService embeddingService, VectorStore vectorStore) {
        this.documentMapper = documentMapper; this.chunkMapper = chunkMapper; this.tagMapper = tagMapper; this.parsers = parsers; this.chunker = chunker;
        this.embeddingService = embeddingService; this.vectorStore = vectorStore;
    }

    public void process(Long documentId) throws Exception {
        // Claim in a short, committed statement so polling and competing workers see the state.
        int claimed = documentMapper.update(null, new UpdateWrapper<Document>().eq("id", documentId).eq("status", "WAITING")
                .set("status", "PARSING").set("error_message", null).set("summary", null));
        if (claimed == 0) return;
        Document document = documentMapper.selectById(documentId);
        if (document == null) return;
        document.setStatus("PARSING");
        document.setSummary(null);
        vectorStore.deleteByDocument(documentId);
        chunkMapper.delete(new QueryWrapper<DocumentChunk>().eq("document_id", documentId));
        tagMapper.delete(new QueryWrapper<DocumentTag>().eq("document_id", documentId));
            DocumentParser parser = parsers.stream().filter(item -> item.supports(document.getFileType())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("没有找到对应的文档解析器"));
            ParsedDocument parsed;
            try (InputStream inputStream = Files.newInputStream(Path.of(document.getFilePath()))) { parsed = parser.parse(inputStream); }
            if (parsed.text().isBlank()) throw new IllegalArgumentException("文档没有可提取的文本；扫描件请先进行 OCR 识别");
            updateStatus(document, "CHUNKING", null);
            List<DocumentChunk> chunks = new ArrayList<>();
            for (ParsedPage page : parsed.pages()) {
                List<TextChunk> pageChunks = chunker.chunk(page.text(), page.pageNumber());
                for (TextChunk item : pageChunks) {
                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setDocumentId(documentId); chunk.setKnowledgeBaseId(document.getKnowledgeBaseId());
                    chunk.setContent(item.content()); chunk.setPageNumber(item.pageNumber()); chunk.setTokenCount(item.tokenCount());
                    chunk.setChunkIndex(chunks.size()); chunks.add(chunk);
                }
            }
            if (chunks.isEmpty()) throw new IllegalArgumentException("文档没有可索引的文本片段");
            chunks.forEach(chunkMapper::insert);
            updateStatus(document, "EMBEDDING", null);
            List<List<Float>> vectors = embeddingService.embedBatch(chunks.stream().map(DocumentChunk::getContent).toList());
            if (vectors.size() != chunks.size()) throw new IllegalStateException("Embedding 数量与 Chunk 数量不一致");
            updateStatus(document, "INDEXING", null);
            for (int i = 0; i < chunks.size(); i++) vectorStore.upsert(chunks.get(i), vectors.get(i));
            document.setPageCount(parsed.pages().size());
            com.example.knowledge.rag.DocumentMetadata metadata;
            try {
                metadata = aiService == null ? extractMetadata(parsed.text()) : aiService.describeDocument(parsed.text());
            } catch (RuntimeException metadataFailure) {
                log.warn("Document metadata generation failed for {}; using marked extractive metadata", documentId);
                metadata = extractMetadata(parsed.text());
            }
            document.setSummary(metadata.summary());
            document.setSummaryMode(metadata.mode());
            saveMetadataTags(documentId, metadata.tags());
            documentMapper.updateById(document);
            if (versionActivation != null) versionActivation.activate(document); else updateStatus(document, "SUCCESS", null);
            if (notificationPublisher != null) notificationPublisher.documentChanged(document, "SUCCESS");
    }

    public void markFailed(Long documentId, Exception exception) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) return;
        boolean alreadyFailed = "FAILED".equals(document.getStatus());
        String message = exception.getMessage() == null ? "文档处理失败" : exception.getMessage();
        try {
            chunkMapper.delete(new QueryWrapper<DocumentChunk>().eq("document_id", documentId));
            tagMapper.delete(new QueryWrapper<DocumentTag>().eq("document_id", documentId));
            try { vectorStore.deleteByDocument(documentId); }
            catch (RuntimeException cleanupFailure) { log.warn("Vector cleanup failed for document {}: {}", documentId, cleanupFailure.getMessage()); }
        } finally {
            updateStatus(document, "FAILED", message.substring(0, Math.min(message.length(), 950)));
            if (!alreadyFailed && notificationPublisher != null) notificationPublisher.documentChanged(document, "FAILED");
        }
    }

    private void updateStatus(Document document, String status, String error) {
        document.setStatus(status); document.setErrorMessage(error);
        documentMapper.update(null, new UpdateWrapper<Document>().eq("id", document.getId())
                .set("status", status).set("error_message", error).set("updated_at", java.time.LocalDateTime.now()));
    }

    private String summary(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "…" : normalized;
    }

    private com.example.knowledge.rag.DocumentMetadata extractMetadata(String text) {
        String[] words = text.replaceAll("[^\\p{L}\\p{N}]+", " ").toLowerCase(Locale.ROOT).split(" ");
        List<String> tags = Arrays.stream(words).filter(word -> word.length() >= 2).map(word -> word.substring(0, Math.min(word.length(), 40)))
                .distinct().sorted(Comparator.comparingInt(String::length).reversed()).limit(5).toList();
        return new com.example.knowledge.rag.DocumentMetadata(summary(text), tags, "EXTRACTIVE");
    }

    private void saveMetadataTags(Long documentId, List<String> tags) {
        tags.stream().filter(word -> word != null && !word.isBlank()).map(word -> word.substring(0, Math.min(word.length(), 40)))
                .distinct().limit(5).forEach(word -> {
            DocumentTag tag = new DocumentTag(); tag.setDocumentId(documentId); tag.setTagName(word); tagMapper.insert(tag);
        });
    }
}
