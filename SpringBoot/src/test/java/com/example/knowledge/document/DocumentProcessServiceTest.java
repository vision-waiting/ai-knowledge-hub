package com.example.knowledge.document;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.entity.DocumentTag;
import com.example.knowledge.mapper.DocumentChunkMapper;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.mapper.DocumentTagMapper;
import com.example.knowledge.rag.EmbeddingService;
import com.example.knowledge.rag.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentProcessServiceTest {
    @TempDir Path temporary;
    private final DocumentMapper documents = mock(DocumentMapper.class);
    private final DocumentChunkMapper chunks = mock(DocumentChunkMapper.class);
    private final DocumentTagMapper tags = mock(DocumentTagMapper.class);
    private final VectorStore vectors = mock(VectorStore.class);
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private Document document;
    private DocumentProcessService processor;

    @BeforeEach void setUp() throws Exception {
        document = new Document();
        document.setId(1L); document.setKnowledgeBaseId(2L); document.setFileType("txt"); document.setStatus("WAITING");
        document.setFilePath(Files.writeString(temporary.resolve("sample.txt"), "Knowledge management content").toString());
        when(documents.selectById(1L)).thenReturn(document);
        when(documents.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(embeddings.embedBatch(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).stream().map(item -> List.of(1f, 0f)).toList());
        processor = new DocumentProcessService(documents, chunks, tags, List.of(new TextParser()), new TextChunker(8, 2), embeddings, vectors);
    }

    @Test void reindexRemovesPreviousChunksTagsAndVectorsBeforeReplacingThem() throws Exception {
        processor.process(1L);
        var ordered = inOrder(vectors, chunks, tags);
        ordered.verify(vectors).deleteByDocument(1L);
        ordered.verify(chunks).delete(any(Wrapper.class));
        ordered.verify(tags).delete(any(Wrapper.class));
        ordered.verify(chunks).insert(any(DocumentChunk.class));
        assertEquals("SUCCESS", document.getStatus());
    }

    @Test void duplicateWorkerCannotClaimTheSameDocument() throws Exception {
        when(documents.update(isNull(), any(Wrapper.class))).thenReturn(0);
        processor.process(1L);
        verifyNoInteractions(embeddings, vectors, chunks, tags);
    }

    @Test void emptyParsedDocumentFailsInsteadOfBecomingSearchable() throws Exception {
        Files.writeString(Path.of(document.getFilePath()), " \n\t ");
        assertThrows(IllegalArgumentException.class, () -> processor.process(1L));
        assertNotEquals("SUCCESS", document.getStatus());
        verifyNoInteractions(embeddings);
    }

    @Test void vectorCleanupOutageDoesNotPreventFailedStatus() {
        document.setStatus("INDEXING");
        doThrow(new IllegalStateException("qdrant unavailable")).when(vectors).deleteByDocument(1L);
        assertDoesNotThrow(() -> processor.markFailed(1L, new IllegalStateException("embedding failed")));
        assertEquals("FAILED", document.getStatus());
    }

    @Test void generatedTagsFitTheDatabaseColumnForLongChineseText() throws Exception {
        Files.writeString(Path.of(document.getFilePath()), "知识管理".repeat(100));
        processor.process(1L);
        verify(tags, atLeastOnce()).insert(argThat((DocumentTag tag) -> tag.getTagName().length() <= 100));
    }

    @Test void successfulProcessingPersistsStructuredAiMetadata() throws Exception {
        com.example.knowledge.rag.AiService ai = mock(com.example.knowledge.rag.AiService.class);
        when(ai.describeDocument(anyString())).thenReturn(new com.example.knowledge.rag.DocumentMetadata("Generated document summary", List.of("Knowledge", "Index"), "OPENAI_COMPATIBLE"));
        org.springframework.test.util.ReflectionTestUtils.setField(processor, "aiService", ai);
        processor.process(1L);
        assertEquals("Generated document summary", document.getSummary());
        assertEquals("OPENAI_COMPATIBLE", document.getSummaryMode());
        verify(tags).insert(argThat((DocumentTag tag) -> "Index".equals(tag.getTagName())));
    }
}
