package com.example.knowledge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final OperationLogInterceptor interceptor;
    private final com.example.knowledge.security.AiRateLimitInterceptor rateLimit;
    public WebConfig(OperationLogInterceptor interceptor, com.example.knowledge.security.AiRateLimitInterceptor rateLimit) { this.interceptor = interceptor; this.rateLimit = rateLimit; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
        registry.addInterceptor(rateLimit).addPathPatterns("/api/chat/stream", "/api/knowledge-bases/*/analysis", "/api/knowledge-bases/*/study", "/api/knowledge-bases/*/retrieval/**", "/api/knowledge-bases/*/web-sources/**");
    }
}
