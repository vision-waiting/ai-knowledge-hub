package com.example.knowledge.security;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end; return n", Long.class);
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int limit;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Window> local = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AiRateLimitInterceptor(StringRedisTemplate redis, @Value("${knowledge.redis-enabled:false}") boolean enabled,
                                  @Value("${knowledge.ai.requests-per-minute:30}") int limit) {
        this(redis, enabled, limit, Clock.systemUTC());
    }
    AiRateLimitInterceptor(StringRedisTemplate redis, boolean enabled, int limit, Clock clock) {
        this.redis = redis; this.enabled = enabled; this.limit = limit; this.clock = clock;
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) return true;
        if (request.getRequestURI().startsWith("/api/knowledge-bases/") && !"POST".equals(request.getMethod())) return true;
        long userId = CurrentUser.require().userId();
        long count;
        if (enabled) {
            try {
                Long result = redis.execute(LIMIT, List.of("knowledge:ai:limit:" + userId));
                if (result == null) throw new IllegalStateException("No rate limit result");
                count = result;
            } catch (org.springframework.data.redis.RedisConnectionFailureException exception) {
                throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "请求限流服务不可用，请稍后重试");
            }
        } else {
            long minute = clock.millis() / 60_000;
            local.entrySet().removeIf(entry -> entry.getValue().minute != minute);
            count = local.compute(userId, (key, old) -> old == null || old.minute != minute ? new Window(minute, 1) : new Window(minute, old.count + 1)).count;
        }
        if (count > limit) { response.setHeader("Retry-After", "60"); throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS); }
        return true;
    }
    private record Window(long minute, long count) {}
}
