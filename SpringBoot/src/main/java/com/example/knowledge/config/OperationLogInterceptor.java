package com.example.knowledge.config;

import com.example.knowledge.entity.OperationLog;
import com.example.knowledge.mapper.OperationLogMapper;
import com.example.knowledge.security.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OperationLogInterceptor implements HandlerInterceptor {
    private final OperationLogMapper mapper;
    public OperationLogInterceptor(OperationLogMapper mapper) { this.mapper = mapper; }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (!request.getRequestURI().startsWith("/api/")) return;
        OperationLog log = new OperationLog();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) log.setUserId(principal.userId());
        log.setOperation(request.getMethod() + " " + request.getRequestURI()); log.setMethod(request.getMethod()); log.setRequestUri(request.getRequestURI()); log.setIp(request.getRemoteAddr()); log.setSuccess(exception == null && response.getStatus() < 400 ? 1 : 0);
        try { mapper.insert(log); }
        catch (RuntimeException loggingFailure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Operation log could not be persisted for {}", request.getRequestURI());
        }
    }
}

