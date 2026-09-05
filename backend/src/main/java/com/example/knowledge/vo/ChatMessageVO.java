package com.example.knowledge.vo;
import java.time.LocalDateTime;
public record ChatMessageVO(Long id, Long sessionId, String role, String content, Integer tokenCount, LocalDateTime createdAt) {}

