package com.first.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.AttractionSummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.entity.AttractionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingCacheServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY = "attraction:ranking:heat:0:20";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /** Mirrors the production ObjectMapper (Spring's builder, JavaTime module auto-registered). */
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private RankingCacheService rankingCacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rankingCacheService = new RankingCacheService(redisTemplate, objectMapper);
    }

    @Test
    void getOrLoad_miss_loadsAndStoresJsonWithTtl() throws Exception {
        when(valueOperations.get(KEY)).thenReturn(null);
        PageResponse<AttractionSummaryResponse> payload = pageResponse("forbidden-city");

        PageResponse<AttractionSummaryResponse> result = rankingCacheService.getOrLoad(
                KEY, TTL, new TypeReference<PageResponse<AttractionSummaryResponse>>() {}, () -> payload);

        assertThat(result).isSameAs(payload);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), json.capture(), eq(TTL));
        assertThat(objectMapper.readValue(json.getValue(),
                new TypeReference<PageResponse<AttractionSummaryResponse>>() {}))
                .usingRecursiveComparison().isEqualTo(payload);
    }

    @Test
    void getOrLoad_hit_returnsCachedValueWithoutLoader() throws Exception {
        PageResponse<AttractionSummaryResponse> cached = pageResponse("great-wall");
        when(valueOperations.get(KEY)).thenReturn(objectMapper.writeValueAsString(cached));
        Supplier<PageResponse<AttractionSummaryResponse>> loader = () -> {
            throw new AssertionError("loader must not run on a cache hit");
        };

        PageResponse<AttractionSummaryResponse> result = rankingCacheService.getOrLoad(
                KEY, TTL, new TypeReference<PageResponse<AttractionSummaryResponse>>() {}, loader);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSlug()).isEqualTo("great-wall");
        assertThat(result.getPage()).isZero();
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getOrLoad_corruptJson_treatsAsMissAndOverwritesEntry() {
        when(valueOperations.get(KEY)).thenReturn("{not-valid-json");
        PageResponse<AttractionSummaryResponse> fresh = pageResponse("forbidden-city");

        PageResponse<AttractionSummaryResponse> result = rankingCacheService.getOrLoad(
                KEY, TTL, new TypeReference<PageResponse<AttractionSummaryResponse>>() {}, () -> fresh);

        assertThat(result).isSameAs(fresh);
        verify(valueOperations).set(eq(KEY), anyString(), eq(TTL));
    }

    @Test
    void getOrLoad_redisReadFailure_fallsBackToLoader() {
        when(valueOperations.get(KEY)).thenThrow(new RuntimeException("redis down"));
        PageResponse<AttractionSummaryResponse> fresh = pageResponse("forbidden-city");

        PageResponse<AttractionSummaryResponse> result = rankingCacheService.getOrLoad(
                KEY, TTL, new TypeReference<PageResponse<AttractionSummaryResponse>>() {}, () -> fresh);

        assertThat(result).isSameAs(fresh);
    }

    @Test
    void getOrLoad_redisWriteFailure_stillReturnsLoadedValue() {
        when(valueOperations.get(KEY)).thenReturn(null);
        doThrow(new RuntimeException("redis down")).when(valueOperations).set(eq(KEY), anyString(), eq(TTL));
        PageResponse<AttractionSummaryResponse> fresh = pageResponse("forbidden-city");

        PageResponse<AttractionSummaryResponse> result = rankingCacheService.getOrLoad(
                KEY, TTL, new TypeReference<PageResponse<AttractionSummaryResponse>>() {}, () -> fresh);

        assertThat(result).isSameAs(fresh);
    }

    @Test
    void evict_deletesOnlyRequestedSortFamily() {
        Set<String> heatKeys = Set.of("attraction:ranking:heat:0:20", "attraction:ranking:heat:1:20");
        when(redisTemplate.keys("attraction:ranking:heat:*")).thenReturn(heatKeys);

        rankingCacheService.evict("heat");

        verify(redisTemplate).delete(heatKeys);
    }

    @Test
    void evictAll_removesRankingAndPopularFamilies() {
        Set<String> rankingKeys = Set.of("attraction:ranking:heat:0:20", "attraction:ranking:popular:0:20");
        Set<String> popularKeys = Set.of("attraction:popular:6");
        when(redisTemplate.keys("attraction:ranking:*")).thenReturn(rankingKeys);
        when(redisTemplate.keys("attraction:popular:*")).thenReturn(popularKeys);

        rankingCacheService.evictAll();

        verify(redisTemplate).delete(rankingKeys);
        verify(redisTemplate).delete(popularKeys);
    }

    @Test
    void evict_whenNoKeysMatch_skipsDelete() {
        when(redisTemplate.keys("attraction:ranking:heat:*")).thenReturn(Set.of());

        rankingCacheService.evict("heat");

        verify(redisTemplate, never()).delete(anyCollection());
    }

    @Test
    void evictAll_whenRedisUnavailable_doesNotThrow() {
        when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> rankingCacheService.evictAll()).doesNotThrowAnyException();
    }

    private PageResponse<AttractionSummaryResponse> pageResponse(String slug) {
        AttractionSummaryResponse item = AttractionSummaryResponse.builder()
                .id(1L)
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .slug(slug)
                .name("Forbidden City")
                .nameZh("故宫")
                .category(AttractionCategory.HISTORICAL_SITE)
                .tags(List.of("unesco"))
                .city("Beijing")
                .citySlug("beijing")
                .summary("Summary")
                .coverImageUrl("https://example.com/cover.jpg")
                .bookingRequired(false)
                .ratingScore(4.9)
                .favoriteCount(6200)
                .heatScore(98)
                .build();
        return new PageResponse<>(List.of(item), 0, 20, 1, 1);
    }
}
