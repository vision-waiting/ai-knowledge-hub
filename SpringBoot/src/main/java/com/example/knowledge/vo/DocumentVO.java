package com.example.knowledge.vo;
import java.time.LocalDateTime;
import java.util.List;
public record DocumentVO(Long id, Long knowledgeBaseId, String fileName, String fileType, Long fileSize,
                         Integer pageCount, String status, String errorMessage, String summary, LocalDateTime createdAt,
                         List<String> tags, String summaryMode, String tagMode, String pageReferenceType) {}

