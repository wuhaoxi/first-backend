package com.first.app.service;

import com.first.app.dto.AttractionResponse;
import com.first.app.dto.AttractionSort;
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

    private static final Sort POPULAR_LIST_SORT = Sort.by(
            Sort.Order.desc("isPopular"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort LATEST_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort RATING_SORT = Sort.by(
            Sort.Order.desc("ratingScore"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort HEAT_SORT = Sort.by(
            Sort.Order.desc("heatScore"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort FAVORITES_SORT = Sort.by(
            Sort.Order.desc("favoriteCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort POPULAR_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AttractionRepository attractionRepository;

    public PageResponse<AttractionSummaryResponse> list(String citySlug, String category, String sort,
                                                         int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        AttractionCategory parsedCategory = parseCategory(category);
        Sort resolvedSort = sortFor(AttractionSort.from(sort));

        Page<AttractionSummaryResponse> result = attractionRepository
                .search(AttractionStatus.PUBLISHED, citySlug, parsedCategory, PageRequest.of(page, size, resolvedSort))
                .map(AttractionSummaryResponse::from);
        return PageResponse.from(result);
    }

    private Sort sortFor(AttractionSort sort) {
        return switch (sort) {
            case LATEST -> LATEST_SORT;
            case RATING -> RATING_SORT;
            case HEAT -> HEAT_SORT;
            case FAVORITES -> FAVORITES_SORT;
            case POPULAR -> POPULAR_LIST_SORT;
        };
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

    public Attraction findByIdPublic(Long id) {
        Attraction attraction = attractionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attraction not found with id: " + id));
        if (attraction.getStatus() != AttractionStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Attraction not found with id: " + id);
        }
        return attraction;
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
