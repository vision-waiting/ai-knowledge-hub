package com.example.knowledge.notifications;

import com.example.knowledge.entity.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class NotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private final NotificationService notifications;
    public NotificationPublisher(NotificationService notifications) { this.notifications = notifications; }

    public void documentChanged(Document document, String type) {
        KnowledgeUpdate event = new KnowledgeUpdate(UUID.randomUUID().toString(), document.getKnowledgeBaseId(),
                document.getId(), document.getFileName(), type, LocalDateTime.now());
        publish(event);
    }

    public void contentChanged(long knowledgeBaseId, String title) {
        publish(new KnowledgeUpdate(UUID.randomUUID().toString(), knowledgeBaseId, null, title, "CONTENT_UPDATED", LocalDateTime.now()));
    }

    private void publish(KnowledgeUpdate event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deliver(event); }
            });
        } else deliver(event);
    }

    private void deliver(KnowledgeUpdate event) {
        try { notifications.record(event); }
        catch (RuntimeException failure) {
            log.warn("Could not store {} notification for document {}; document operation remains successful", event.type(), event.documentId(), failure);
        }
    }
}
