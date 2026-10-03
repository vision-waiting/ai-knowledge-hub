package com.example.knowledge.security;

public record JwtPrincipal(Long userId, String username, String role) {
}

