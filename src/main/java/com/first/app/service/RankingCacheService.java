package com.first.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Redis cache-aside layer for attraction ranking reads.
 *
 * <p>Reads are best-effort: any Redis or serialization failure degrades to the supplied
 * loader (database) with a WARN log — cache availability never affects endpoint
 * availability. The loader closure keeps all repository access in the calling service,
 * so the dependency stays one-way ({@code AttractionService} → {@code RankingCacheService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, type);
            }
        } catch (Exception e) {
            log.warn("Ranking cache read failed for key {} — falling back to loader: {}", key, e.getMessage());
        }

        T value = loader.get();

        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Ranking cache write failed for key {} — serving uncached value: {}", key, e.getMessage());
        }

        return value;
    }

    /** Deletes the whole ranking family of one sort, e.g. {@code attraction:ranking:heat:*}. */
    public void evict(String sort) {
        deleteKeys("attraction:ranking:" + sort + ":*");
    }

    /** Deletes every ranking and popular entry — called when attraction data changes. */
    public void evictAll() {
        deleteKeys("attraction:ranking:*");
        deleteKeys("attraction:popular:*");
    }

    private void deleteKeys(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Ranking cache eviction failed for pattern {} — entries will expire via TTL: {}", pattern,
                    e.getMessage());
        }
    }
}
