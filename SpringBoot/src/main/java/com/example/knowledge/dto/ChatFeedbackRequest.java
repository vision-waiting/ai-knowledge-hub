package com.example.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatFeedbackRequest(@NotBlank String feedback, @Size(max = 200) String reason) {}
