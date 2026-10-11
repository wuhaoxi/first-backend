package com.first.app.service;

import com.first.app.config.AssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantRateLimiterTest {

    private static final long USER_ID = 42L;
    private static final int LIMIT = 10;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AssistantRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new AssistantRateLimiter(redisTemplate,
                new AssistantProperties("sk-test", Duration.ofHours(24), 20, 6000, 2000, LIMIT));
    }

    @Test
    void tryAcquire_underLimit_allows() {
        when(valueOperations.increment(anyString())).thenReturn(3L);

        assertThat(rateLimiter.tryAcquire(USER_ID)).isTrue();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void tryAcquire_atLimit_allows() {
        when(valueOperations.increment(anyString())).thenReturn((long) LIMIT);

        assertThat(rateLimiter.tryAcquire(USER_ID)).isTrue();
    }

    @Test
    void tryAcquire_overLimit_denies() {
        when(valueOperations.increment(anyString())).thenReturn((long) LIMIT + 1);

        assertThat(rateLimiter.tryAcquire(USER_ID)).isFalse();
    }

    @Test
    void tryAcquire_firstRequestInWindow_setsExpiryOnMinuteKey() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertThat(rateLimiter.tryAcquire(USER_ID)).isTrue();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(key.capture());
        assertThat(key.getValue()).matches("assistant:rate:42:\\d+");
        verify(redisTemplate).expire(eq(key.getValue()), eq(Duration.ofSeconds(120)));
    }

    @Test
    void tryAcquire_subsequentRequest_keepsExistingExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(2L);

        assertThat(rateLimiter.tryAcquire(USER_ID)).isTrue();

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void tryAcquire_redisFailure_failsOpen() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(rateLimiter.tryAcquire(USER_ID)).isTrue();
    }
}
