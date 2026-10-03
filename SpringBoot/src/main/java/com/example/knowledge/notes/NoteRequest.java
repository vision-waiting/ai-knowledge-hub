package com.example.knowledge.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record NoteRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200000) String content,
        @Size(max = 80) String category,
        @Positive Long sourceSessionId) {}
