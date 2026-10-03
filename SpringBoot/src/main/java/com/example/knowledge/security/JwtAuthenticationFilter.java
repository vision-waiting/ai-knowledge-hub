package com.example.knowledge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokenProvider;
    private final com.example.knowledge.mapper.SysUserMapper users;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, com.example.knowledge.mapper.SysUserMapper users) {
        this.tokenProvider = tokenProvider;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                JwtPrincipal identity = tokenProvider.parse(token);
                var user = users.selectById(identity.userId());
                if (user == null || !Integer.valueOf(1).equals(user.getStatus()) ||
                        tokenProvider.version(token) != (user.getTokenVersion() == null ? 0 : user.getTokenVersion())) {
                    throw new IllegalArgumentException("Invalid user session");
                }
                JwtPrincipal principal = new JwtPrincipal(user.getId(), user.getUsername(), user.getRole());
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}

