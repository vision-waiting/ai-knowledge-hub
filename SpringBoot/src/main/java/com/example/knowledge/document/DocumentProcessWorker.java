package com.example.knowledge.document;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessWorker {
    private final DocumentProcessService processor;
    public DocumentProcessWorker(DocumentProcessService processor) { this.processor = processor; }
    @Async
    public void processAsync(Long documentId) {
        try { processor.process(documentId); }
        catch (Exception exception) { processor.markFailed(documentId, exception); }
    }
}
