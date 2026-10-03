package com.example.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.document.DocumentProcessWorker;
import com.example.knowledge.document.StorageService;
import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.*;
import com.example.knowledge.rag.VectorStore;
import com.example.knowledge.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    private final DocumentMapper documents = mock(DocumentMapper.class);
    private final DocumentChunkMapper chunks = mock(DocumentChunkMapper.class);
    private final DocumentTagMapper tags = mock(DocumentTagMapper.class);
    private final KnowledgeBaseMapper bases = mock(KnowledgeBaseMapper.class);
    private final StorageService storage = mock(StorageService.class);
    private final DocumentProcessWorker worker = mock(DocumentProcessWorker.class);
    private final VectorStore vectors = mock(VectorStore.class);
    private final PermissionService permission = mock(PermissionService.class);
    private final JwtPrincipal owner = new JwtPrincipal(1L, "owner", "USER");
    private Document document;
    private DocumentService service;

    @BeforeEach void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        document = new Document(); document.setId(1L); document.setKnowledgeBaseId(2L);
        document.setStatus("SUCCESS"); document.setFilePath("sample.txt");
        when(documents.selectById(1L)).thenReturn(document);
        when(documents.selectOne(any(Wrapper.class))).thenReturn(document);
        when(bases.selectOne(any(Wrapper.class))).thenReturn(new KnowledgeBase());
        service = new DocumentService(documents, chunks, tags, bases, permission, storage, worker, vectors);
    }

    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void deletionPreservesHistoricalFileAndChunks() throws Exception {
        service.delete(1L, owner);
        verify(documents, never()).deleteById(anyLong());
        verify(chunks, never()).delete(any(Wrapper.class));
        verifyNoInteractions(storage, vectors);
        assertEquals("TRASHED", document.getStatus());
    }
    @Test void restoreRequiresWritePermission() throws Exception {
        document.setStatus("TRASHED");
        doThrow(new BusinessException(com.example.knowledge.common.ErrorCode.FORBIDDEN))
                .when(permission).requireKnowledgeBaseAccess(2L, owner, true);
        var exception = assertThrows(BusinessException.class, () -> invokeRestore());
        assertEquals(com.example.knowledge.common.ErrorCode.FORBIDDEN, exception.errorCode());
        verify(documents, never()).updateById(any(Document.class));
    }

    @Test void restoreRejectsAnotherCurrentVersion() throws Exception {
        document.setStatus("TRASHED");
        when(documents.selectCount(any(Wrapper.class))).thenReturn(1L);
        var exception = assertThrows(BusinessException.class, () -> invokeRestore());
        assertEquals(com.example.knowledge.common.ErrorCode.CONFLICT, exception.errorCode());
        verifyNoInteractions(worker);
    }

    @Test void historicalVersionRequiresReadPermission() {
        document.setStatus("ARCHIVED");
        doThrow(new BusinessException(com.example.knowledge.common.ErrorCode.FORBIDDEN))
                .when(permission).requireKnowledgeBaseAccess(2L, owner, false);
        assertThrows(BusinessException.class, () -> service.downloadPath(1L, owner));
        assertThrows(BusinessException.class, () -> service.chunks(1L, owner));
        verifyNoInteractions(storage, chunks);
    }

    private void invokeRestore() throws Exception {
        try { DocumentService.class.getMethod("restore", Long.class, JwtPrincipal.class).invoke(service, 1L, owner); }
        catch (java.lang.reflect.InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw exception;
        }
    }
    @Test void reindexIsSubmittedOnlyAfterTransactionCommit() {
        document.setStatus("FAILED");
        assertEquals("WAITING", service.reindex(1L, owner).status());
        verifyNoInteractions(worker);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(worker).processAsync(1L);
    }

    @Test void runningDocumentCannotBeDeleted() {
        document.setStatus("EMBEDDING");
        assertThrows(BusinessException.class, () -> service.delete(1L, owner));
        verifyNoInteractions(storage, vectors, chunks, tags);
    }

    @Test void runningDocumentCannotBeQueuedAgain() {
        document.setStatus("WAITING");
        assertThrows(BusinessException.class, () -> service.reindex(1L, owner));
        verifyNoInteractions(worker);
    }

    @Test void committedDeletionSurvivesLegacyPathAndContinuesCleaningOtherFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var uploadRoot = java.nio.file.Files.createDirectory(temp.resolve("uploads"));
        var outside = java.nio.file.Files.writeString(temp.resolve("legacy.txt"), "keep outside file");
        var inside = java.nio.file.Files.writeString(uploadRoot.resolve("current.txt"), "remove uploaded file");
        document.setFilePath(outside.toString());
        Document current = new Document(); current.setId(2L); current.setKnowledgeBaseId(2L);
        current.setStatus("SUCCESS"); current.setFilePath(inside.toString());
        when(documents.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(document, current));
        service = new DocumentService(documents, chunks, tags, bases, permission,
                new StorageService(uploadRoot.toString()), worker, vectors);

        service.deleteKnowledgeBaseDocuments(2L);
        assertTrue(java.nio.file.Files.exists(inside), "cleanup must wait for commit");
        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit()));
        assertTrue(java.nio.file.Files.exists(outside), "outside files must remain protected");
        assertFalse(java.nio.file.Files.exists(inside), "one invalid path must not stop later cleanup");
        verify(documents).delete(any(Wrapper.class));
    }
}
