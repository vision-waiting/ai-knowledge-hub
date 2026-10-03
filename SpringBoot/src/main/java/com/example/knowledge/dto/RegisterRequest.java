package com.example.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{4,32}", message = "用户名须为4–32位字母、数字或下划线") String username,
        @Size(max = 50, message = "昵称不能超过50个字符") String nickname,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度须为8–72个字符") String password,
        @NotBlank(message = "请再次输入密码")
        @Size(max = 72, message = "确认密码不能超过72个字符") String confirmPassword) {
    public RegisterRequest {
        username = username == null ? null : username.trim();
        nickname = nickname == null ? null : nickname.trim();
    }
}
