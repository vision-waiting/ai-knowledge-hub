package com.example.knowledge.vo;
import java.time.LocalDateTime;
public record DocumentVO(Long id, Long knowledgeBaseId, String fileName, String fileType, Long fileSize,
                         Integer pageCount, String status, String errorMessage, String summary, LocalDateTime createdAt) {}

