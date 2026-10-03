package com.example.knowledge.security;

import com.example.knowledge.common.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class AiRateLimitInterceptorTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void rejectsOverQuotaWithoutAffectingAnotherUser() {
        var limiter = new AiRateLimitInterceptor(null, false, 2, Clock.fixed(Instant.parse("2026-09-10T10:00:00Z"), ZoneOffset.UTC));
        var request = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        login(1L);
        assertTrue(limiter.preHandle(request, response, new Object()));
        assertTrue(limiter.preHandle(request, response, new Object()));
        BusinessException error = assertThrows(BusinessException.class, () -> limiter.preHandle(request, response, new Object()));
        assertEquals(429, error.errorCode().code());
        assertEquals("60", response.getHeader("Retry-After"));
        login(2L); assertTrue(limiter.preHandle(request, response, new Object()));
    }
    private void login(long id) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new JwtPrincipal(id, "user", "USER"), null, java.util.List.of())); }

    @Test void browsingAnalysisHistoryDoesNotConsumeGenerationQuota() {
        var limiter = new AiRateLimitInterceptor(null, false, 1, Clock.systemUTC());
        login(1L);
        var history = new MockHttpServletRequest("GET", "/api/knowledge-bases/3/analysis");
        var generation = new MockHttpServletRequest("POST", "/api/knowledge-bases/3/study");
        var response = new MockHttpServletResponse();
        assertTrue(limiter.preHandle(history, response, new Object()));
        assertTrue(limiter.preHandle(history, response, new Object()));
        assertTrue(limiter.preHandle(generation, response, new Object()));
        assertThrows(BusinessException.class, () -> limiter.preHandle(generation, response, new Object()));
    }
}
