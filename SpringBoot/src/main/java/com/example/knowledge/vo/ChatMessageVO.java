package com.example.knowledge.vo;
import java.time.LocalDateTime;
import java.util.List;
import com.example.knowledge.chat.RetrievalTrace;
import com.example.knowledge.rag.Citation;
public record ChatMessageVO(Long id, Long sessionId, String role, String content, Integer tokenCount,
                            LocalDateTime createdAt, String mode, List<Citation> citations, String feedback, String feedbackReason,
                            RetrievalTrace retrieval, String model) {
    public ChatMessageVO(Long id, Long sessionId, String role, String content, Integer tokenCount, LocalDateTime createdAt, String mode,
                         List<Citation> citations, String feedback, String feedbackReason, RetrievalTrace retrieval) {
        this(id, sessionId, role, content, tokenCount, createdAt, mode, citations, feedback, feedbackReason, retrieval, null);
    }
    public ChatMessageVO(Long id, Long sessionId, String role, String content, Integer tokenCount, LocalDateTime createdAt, String mode,
                         List<Citation> citations, String feedback, String feedbackReason) {
        this(id, sessionId, role, content, tokenCount, createdAt, mode, citations, feedback, feedbackReason, null);
    }
    public ChatMessageVO(Long id, Long sessionId, String role, String content, Integer tokenCount, LocalDateTime createdAt, String mode, List<Citation> citations) {
        this(id, sessionId, role, content, tokenCount, createdAt, mode, citations, null, null);
    }
    public ChatMessageVO(Long id, Long sessionId, String role, String content, Integer tokenCount, LocalDateTime createdAt) {
        this(id, sessionId, role, content, tokenCount, createdAt, null, List.of());
    }
}
