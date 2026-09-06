package com.example.knowledge.document;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.entity.DocumentTag;
import com.example.knowledge.mapper.DocumentChunkMapper;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.mapper.DocumentTagMapper;
import com.example.knowledge.rag.EmbeddingService;
import com.example.knowledge.rag.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class DocumentProcessService {
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentTagMapper tagMapper;
    private final List<DocumentParser> parsers;
    private final TextChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public DocumentProcessService(DocumentMapper documentMapper, DocumentChunkMapper chunkMapper,
                                  DocumentTagMapper tagMapper, List<DocumentParser> parsers, TextChunker chunker,
                                  EmbeddingService embeddingService, VectorStore vectorStore) {
        this.documentMapper = documentMapper; this.chunkMapper = chunkMapper; this.tagMapper = tagMapper; this.parsers = parsers; this.chunker = chunker;
        this.embeddingService = embeddingService; this.vectorStore = vectorStore;
    }

    @Transactional
    public void process(Long documentId) throws Exception {
        Document document = documentMapper.selectById(documentId);
        if (document == null) return;
        updateStatus(document, "PARSING", null);
            DocumentParser parser = parsers.stream().filter(item -> item.supports(document.getFileType())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("没有找到对应的文档解析器"));
            ParsedDocument parsed;
            try (InputStream inputStream = Files.newInputStream(Path.of(document.getFilePath()))) { parsed = parser.parse(inputStream); }
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
            chunks.forEach(chunkMapper::insert);
            updateStatus(document, "EMBEDDING", null);
            List<List<Float>> vectors = embeddingService.embedBatch(chunks.stream().map(DocumentChunk::getContent).toList());
            if (vectors.size() != chunks.size()) throw new IllegalStateException("Embedding 数量与 Chunk 数量不一致");
            updateStatus(document, "INDEXING", null);
            for (int i = 0; i < chunks.size(); i++) vectorStore.upsert(chunks.get(i), vectors.get(i));
            document.setPageCount(parsed.pages().size());
            document.setSummary(summary(parsed.text()));
            document.setStatus("SUCCESS"); document.setErrorMessage(null); documentMapper.updateById(document);
            saveTags(documentId, parsed.text());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long documentId, Exception exception) {
        chunkMapper.delete(new QueryWrapper<DocumentChunk>().eq("document_id", documentId));
        tagMapper.delete(new QueryWrapper<DocumentTag>().eq("document_id", documentId));
        vectorStore.deleteByDocument(documentId);
        Document document = documentMapper.selectById(documentId);
        if (document != null) { document.setStatus("FAILED"); document.setErrorMessage(exception.getMessage() == null ? "文档处理失败" : exception.getMessage()); documentMapper.updateById(document); }
    }

    private void updateStatus(Document document, String status, String error) {
        document.setStatus(status); document.setErrorMessage(error); documentMapper.updateById(document);
    }

    private String summary(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "…" : normalized;
    }

    private void saveTags(Long documentId, String text) {
        String[] words = text.replaceAll("[^\\p{L}\\p{N}]+", " ").toLowerCase().split(" ");
        Arrays.stream(words).filter(word -> word.length() >= 2).distinct().sorted(Comparator.comparingInt(String::length).reversed()).limit(5).forEach(word -> {
            DocumentTag tag = new DocumentTag(); tag.setDocumentId(documentId); tag.setTagName(word); tagMapper.insert(tag);
        });
    }
}
