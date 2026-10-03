package com.example.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}", message = "用户名需为3到50位字母、数字或下划线") String username,
                                @NotBlank @Size(min = 8, max = 72) String password,
                                @Size(max = 100) String nickname,
                                @NotBlank @Pattern(regexp = "ADMIN|USER") String role) {}
