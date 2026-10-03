package com.example.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空") @Size(max = 100, message = "知识库名称不能超过100字") String name,
        @Size(max = 500, message = "知识库描述不能超过500字") String description, Boolean shared) {
    public KnowledgeBaseRequest(String name, String description) { this(name, description, null); }
}

