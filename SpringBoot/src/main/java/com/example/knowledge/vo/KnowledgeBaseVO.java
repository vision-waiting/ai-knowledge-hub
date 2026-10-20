package com.example.knowledge.vo;

import java.time.LocalDateTime;

public record KnowledgeBaseVO(Long id, String name, String description, Long ownerId, Integer documentCount,
                              LocalDateTime createdAt, LocalDateTime updatedAt, String ownerUsername, String ownerNickname, boolean shared, boolean sharingLocked, boolean canWrite, boolean canManage, boolean starred, Long starCount) {
}

