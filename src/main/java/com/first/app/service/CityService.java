package com.first.app.service;

import com.first.app.dto.CityResponse;
import com.first.app.dto.CitySummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CityService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.asc("name"), Sort.Order.asc("id"));

    private final CityRepository cityRepository;
    private final AttractionRepository attractionRepository;

    public PageResponse<CitySummaryResponse> list(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Page<CitySummaryResponse> result = cityRepository
                .findByStatus(CityStatus.PUBLISHED, PageRequest.of(page, size, LIST_SORT))
                .map(CitySummaryResponse::from);
        return PageResponse.from(result);
    }

    public CityResponse getBySlug(String slug) {
        City city = cityRepository.findBySlugAndStatus(slug, CityStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("City not found: " + slug));
        long attractionCount = attractionRepository
                .countByCitySlugAndStatus(city.getSlug(), AttractionStatus.PUBLISHED);
        return CityResponse.from(city, attractionCount);
    }
}
