package com.example.knowledge.chat;

public record ChatMetadata(Long sessionId, String mode, Long messageId, RetrievalTrace retrieval, String model) {
    public ChatMetadata(Long sessionId, String mode, Long messageId, RetrievalTrace retrieval) { this(sessionId, mode, messageId, retrieval, null); }
    public ChatMetadata(Long sessionId, String mode) { this(sessionId, mode, null, null); }
    public ChatMetadata(Long sessionId, String mode, Long messageId) { this(sessionId, mode, messageId, null); }
}
