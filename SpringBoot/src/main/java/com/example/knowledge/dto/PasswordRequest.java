package com.example.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordRequest(@NotBlank String currentPassword,
                              @NotBlank @Size(min = 8, max = 72, message = "新密码需为8到72个字符") String newPassword) {}
