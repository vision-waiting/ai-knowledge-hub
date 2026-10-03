package com.example.knowledge.dto;

import jakarta.validation.constraints.*;

public record UserUpdateRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}", message = "用户名需为3到50位字母、数字、下划线、点或短横线") String username,
        @Size(max = 100) String nickname,
        @NotBlank @Pattern(regexp = "ADMIN|USER") String role,
        @NotNull @Min(0) @Max(1) Integer status,
        @Size(max = 72) String password) {}
