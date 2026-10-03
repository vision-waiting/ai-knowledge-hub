package com.example.knowledge.notifications;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.knowledge.document.*;
import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.*;
import com.example.knowledge.rag.EmbeddingService;
import com.example.knowledge.rag.VectorStore;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.DocumentService;
import com.example.knowledge.service.PermissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises actual document entry points with the real after-commit publisher. */
class DocumentNotificationIntegrationTest {
    @TempDir Path temporary;
    final DocumentMapper documents = mock(DocumentMapper.class);
    final DocumentChunkMapper chunks = mock(DocumentChunkMapper.class);
    final DocumentTagMapper tags = mock(DocumentTagMapper.class);
    final KnowledgeBaseMapper bases = mock(KnowledgeBaseMapper.class);
    final StorageService storage = mock(StorageService.class);
    final DocumentProcessWorker worker = mock(DocumentProcessWorker.class);
    final VectorStore vectors = mock(VectorStore.class);
    final PermissionService permissions = mock(PermissionService.class);
    final NotificationService notifications = mock(NotificationService.class);
    final NotificationPublisher publisher = new NotificationPublisher(notifications);
    final JwtPrincipal owner = new JwtPrincipal(1L, "owner", "USER");
    Document document;
    DocumentService service;

    @BeforeEach void setup() throws Exception {
        document = new Document(); document.setId(20L); document.setKnowledgeBaseId(10L); document.setFileName("lecture.txt");
        document.setStatus("SUCCESS"); document.setFileType("txt");
        document.setFilePath(Files.writeString(temporary.resolve("lecture.txt"), "Knowledge management helps organize documents and study materials.").toString());
        when(documents.selectById(20L)).thenReturn(document);
        when(documents.selectOne(any(Wrapper.class))).thenReturn(document);
        when(documents.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(bases.selectOne(any(Wrapper.class))).thenReturn(new KnowledgeBase());
        service = new DocumentService(documents, chunks, tags, bases, permissions, storage, worker, vectors);
        ReflectionTestUtils.setField(service, "notificationPublisher", publisher);
    }
    @AfterEach void cleanup() { if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization(); }
    void commit() { TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit()); }

    @Test void uploadEmitsOneEventOnlyAfterCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        MockMultipartFile file = new MockMultipartFile("file", "lecture.txt", "text/plain", "lecture".getBytes());
        when(storage.save(eq(10L), any())).thenReturn(new StorageService.StoredFile(document.getFilePath(), "txt", "lecture.txt"));
        doAnswer(invocation -> { ((Document) invocation.getArgument(0)).setId(20L); return 1; }).when(documents).insert(any(Document.class));
        service.upload(10L, file, owner);
        verifyNoInteractions(notifications);
        commit();
        verify(notifications).record(argThat(event -> event.type().equals("UPLOADED") && event.documentId().equals(20L)));
    }

    @Test void reindexAndDeletionEmitDocumentSnapshotsAfterCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        service.reindex(20L, owner);
        verifyNoInteractions(notifications);
        commit();
        verify(notifications).record(argThat(event -> event.type().equals("REINDEXED")));
        TransactionSynchronizationManager.clearSynchronization(); TransactionSynchronizationManager.initSynchronization();
        document.setStatus("SUCCESS");
        service.delete(20L, owner); commit();
        verify(notifications).record(argThat(event -> event.type().equals("DELETED") && event.documentName().equals("lecture.txt")));
    }

    @Test void processingSuccessAndFailureEmitEventsWithoutChangingTheirResults() throws Exception {
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embedBatch(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).stream().map(item -> List.of(1f, 0f)).toList());
        DocumentProcessService processor = new DocumentProcessService(documents, chunks, tags, List.of(new TextParser()), new TextChunker(8, 2), embeddings, vectors);
        ReflectionTestUtils.setField(processor, "notificationPublisher", publisher);
        document.setStatus("WAITING");
        doThrow(new IllegalStateException("notification storage unavailable")).when(notifications).record(any());
        processor.process(20L);
        assertThat(document.getStatus()).isEqualTo("SUCCESS");
        verify(notifications).record(argThat(event -> event.type().equals("SUCCESS")));
        document.setStatus("PARSING");
        processor.markFailed(20L, new IllegalArgumentException("empty document"));
        processor.markFailed(20L, new IllegalArgumentException("empty document"));
        assertThat(document.getStatus()).isEqualTo("FAILED");
        verify(notifications, times(1)).record(argThat(event -> event.type().equals("FAILED")));
    }
}
