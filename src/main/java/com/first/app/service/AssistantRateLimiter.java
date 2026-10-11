package com.first.app.service;

import com.first.app.config.AssistantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Per-user sliding rate limiter for the assistant chat endpoint (work item 2.x of add-ai-assistant).
 *
 * <p>Counts requests in {@code assistant:rate:{userId}:{epochMinute}} with an {@code INCR} plus a
 * 120 s expiry so the key self-cleans well past its minute bucket. Redis failures fail open —
 * a limiter outage must never take chat down with it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantRateLimiter {

    /** Long enough to cover the minute bucket plus clock skew between nodes. */
    private static final Duration WINDOW_TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate;
    private final AssistantProperties properties;

    /** True when the user is within {@code app.assistant.rate-limit-per-minute} for the current minute. */
    public boolean tryAcquire(long userId) {
        String key = key(userId);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW_TTL);
            }
            return count == null || count <= properties.rateLimitPerMinute();
        } catch (Exception e) {
            log.warn("Rate limiter unavailable for key {} — failing open: {}", key, e.getMessage());
            return true;
        }
    }

    private static String key(long userId) {
        return "assistant:rate:" + userId + ":" + (System.currentTimeMillis() / 60_000);
    }
}
