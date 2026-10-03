package com.example.knowledge.notes;

import java.time.LocalDateTime;

public record NoteVO(Long id, String title, String content, String category, Long sourceSessionId,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {}
