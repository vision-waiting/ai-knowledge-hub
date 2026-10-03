package com.example.knowledge.vo;

public record UserVO(Long id, String username, String nickname, String role, Integer status,
                     java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {
    public UserVO(Long id, String username, String nickname, String role, Integer status) { this(id, username, nickname, role, status, null, null); }
    public UserVO(Long id, String username, String nickname, String role) { this(id, username, nickname, role, 1); }
}
