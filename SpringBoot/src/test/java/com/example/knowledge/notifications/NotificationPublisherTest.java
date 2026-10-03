package com.example.knowledge.notifications;

import com.example.knowledge.entity.Document;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationPublisherTest {
    NotificationService service = mock(NotificationService.class);
    NotificationPublisher publisher = new NotificationPublisher(service);
    Document document = new Document();
    @BeforeEach void setup() { document.setId(20L); document.setKnowledgeBaseId(10L); document.setFileName("讲义.txt"); }
    @AfterEach void cleanup() { if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization(); }

    @Test void doesNotPublishBeforeCommitOrOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        publisher.documentChanged(document, "UPLOADED");
        verifyNoInteractions(service);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(service);
    }
    @Test void snapshotsEventBeforeCommitAndIsolatesStorageFailure() {
        TransactionSynchronizationManager.initSynchronization();
        publisher.documentChanged(document, "DELETED");
        document.setFileName("changed.txt");
        doThrow(new IllegalStateException("database unavailable")).when(service).record(any());
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        verify(service).record(argThat(e -> e.documentName().equals("讲义.txt") && e.type().equals("DELETED")));
    }
}
