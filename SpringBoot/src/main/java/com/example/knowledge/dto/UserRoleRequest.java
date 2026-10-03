package com.example.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserRoleRequest(@NotBlank @Pattern(regexp = "ADMIN|USER") String role) {}
