package com.first.app.service;

import com.first.app.dto.AttractionResponse;
import com.first.app.dto.AttractionSummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.AttractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttractionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_POPULAR_LIMIT = 1;
    private static final int MAX_POPULAR_LIMIT = 12;

    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.desc("isPopular"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort POPULAR_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AttractionRepository attractionRepository;

    public PageResponse<AttractionSummaryResponse> list(String citySlug, String category, int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        AttractionCategory parsedCategory = parseCategory(category);

        Page<AttractionSummaryResponse> result = attractionRepository
                .search(AttractionStatus.PUBLISHED, citySlug, parsedCategory, PageRequest.of(page, size, LIST_SORT))
                .map(AttractionSummaryResponse::from);
        return PageResponse.from(result);
    }

    public List<AttractionSummaryResponse> popular(int limit) {
        if (limit < MIN_POPULAR_LIMIT || limit > MAX_POPULAR_LIMIT) {
            throw new InvalidRequestException(
                    "limit must be between " + MIN_POPULAR_LIMIT + " and " + MAX_POPULAR_LIMIT);
        }
        return attractionRepository
                .findByStatusAndIsPopularTrue(AttractionStatus.PUBLISHED, PageRequest.of(0, limit, POPULAR_SORT))
                .stream()
                .map(AttractionSummaryResponse::from)
                .toList();
    }

    public AttractionResponse getBySlug(String slug) {
        return attractionRepository.findBySlugAndStatus(slug, AttractionStatus.PUBLISHED)
                .map(AttractionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Attraction not found: " + slug));
    }

    private AttractionCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return AttractionCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid category: " + category
                    + ". Valid values: " + Arrays.toString(AttractionCategory.values()));
        }
    }
}
