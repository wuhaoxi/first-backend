package com.first.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Structured attraction retrieval for the assistant tool. Turns the tool arguments
 * ({@code city?, category?, keyword?, limit?}) into a deterministic query over the existing
 * published-attraction search: the city is resolved against published city data (slug exact,
 * name/nameZh case-insensitive), invalid parameters yield the frozen
 * {@code {"error":{"kind":"input-rejection","reason":"…"}}} text envelope, and hits become a
 * compact JSON row array (blank fields omitted) with tickets/hours/booking facts. Every
 * degradation scenario — no match, unknown city, database failure — yields a short plain-text
 * notice instead of an exception, so the SSE chat stream always stays alive.
 */
@Slf4j
@Service
public class AttractionSearchService {

    private static final String NO_MATCH_TEXT = "No published attractions matched this search.";

    private static final String UNAVAILABLE_TEXT =
            "Attraction data is currently unavailable; please try again later.";

    private static final int DEFAULT_LIMIT = 5;

    private static final int MAX_LIMIT = 10;

    private final CityRepository cityRepository;

    private final AttractionRepository attractionRepository;

    private final ObjectMapper objectMapper;

    public AttractionSearchService(CityRepository cityRepository,
            AttractionRepository attractionRepository, ObjectMapper objectMapper) {
        this.cityRepository = cityRepository;
        this.attractionRepository = attractionRepository;
        this.objectMapper = objectMapper;
    }

    /** Searches published attractions; never throws. */
    public String search(String city, String category, String keyword, Integer limit) {
        AttractionCategory parsedCategory;
        int effectiveLimit;
        try {
            parsedCategory = parseCategory(category);
            effectiveLimit = validateLimit(limit);
        } catch (IllegalArgumentException e) {
            return inputRejection(e.getMessage());
        }
        try {
            String citySlug = null;
            if (StringUtils.hasText(city)) {
                citySlug = resolveCitySlug(city.strip());
                if (citySlug == null) {
                    return NO_MATCH_TEXT;
                }
            }
            String escapedKeyword = escapeLike(normalizeKeyword(keyword));
            Page<Attraction> page = attractionRepository.search(AttractionStatus.PUBLISHED,
                    citySlug, parsedCategory, escapedKeyword,
                    PageRequest.of(0, effectiveLimit, Sort.by(Sort.Direction.DESC, "heatScore")));
            if (page.isEmpty()) {
                return NO_MATCH_TEXT;
            }
            return toJson(page.getContent());
        } catch (RuntimeException e) {
            log.warn("Attraction search unavailable: {}", e.getMessage());
            return UNAVAILABLE_TEXT;
        }
    }

    private String resolveCitySlug(String city) {
        for (City candidate : cityRepository.findByStatus(CityStatus.PUBLISHED)) {
            if (candidate.getSlug().equals(city)
                    || candidate.getName().equalsIgnoreCase(city)
                    || candidate.getNameZh().equalsIgnoreCase(city)) {
                return candidate.getSlug();
            }
        }
        return null;
    }

    private static AttractionCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return AttractionCategory.valueOf(category.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid category: " + category
                    + ". Valid values: " + Arrays.toString(AttractionCategory.values()));
        }
    }

    private static int validateLimit(Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT : limit;
        if (effective < 1 || effective > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return effective;
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Escapes JPQL LIKE wildcards ({@code !}, {@code %}, {@code _}) so the keyword matches literally. */
    private static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String toJson(List<Attraction> attractions) {
        List<Map<String, Object>> rows = new ArrayList<>(attractions.size());
        for (Attraction attraction : attractions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nameZh", attraction.getNameZh());
            row.put("name", attraction.getName());
            row.put("slug", attraction.getSlug());
            row.put("city", attraction.getCity());
            row.put("category", attraction.getCategory().name());
            row.put("summary", attraction.getSummary());
            row.put("ratingScore", attraction.getRatingScore());
            putIfHasText(row, "ticketPrice", attraction.getTicketPrice());
            putIfHasText(row, "openingHours", attraction.getOpeningHours());
            if (attraction.isBookingRequired()) {
                row.put("bookingRequired", true);
            }
            putIfHasText(row, "suggestedDuration", attraction.getSuggestedDuration());
            putIfHasText(row, "address", attraction.getAddress());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("attraction search serialization failed", e);
        }
    }

    private static void putIfHasText(Map<String, Object> row, String key, String value) {
        if (StringUtils.hasText(value)) {
            row.put(key, value);
        }
    }

    private String inputRejection(String reason) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("error", Map.of("kind", "input-rejection", "reason", reason)));
        } catch (JsonProcessingException e) {
            return "{\"error\":{\"kind\":\"input-rejection\",\"reason\":\"invalid input\"}}";
        }
    }
}
