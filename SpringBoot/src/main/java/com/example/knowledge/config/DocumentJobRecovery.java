package com.example.knowledge.config;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.knowledge.entity.Document;
import com.example.knowledge.mapper.DocumentMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentJobRecovery {
    private final DocumentMapper documents;
    public DocumentJobRecovery(DocumentMapper documents) { this.documents = documents; }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        // The supported deployment is a single worker instance. Preserve files for explicit retry.
        documents.update(null, new UpdateWrapper<Document>().in("status", "WAITING", "PARSING", "CHUNKING", "EMBEDDING", "INDEXING")
                .set("status", "FAILED").set("error_message", "上次处理因服务停止而中断，请重新索引")
                .set("updated_at", java.time.LocalDateTime.now()));
    }
}
