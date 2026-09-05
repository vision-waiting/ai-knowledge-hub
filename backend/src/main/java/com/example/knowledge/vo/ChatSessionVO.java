package com.example.knowledge.vo;
import java.time.LocalDateTime;
public record ChatSessionVO(Long id, Long userId, Long knowledgeBaseId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}

