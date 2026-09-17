package com.first.app.controller;

import com.first.app.dto.CityResponse;
import com.first.app.dto.CitySummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @GetMapping
    public ResponseEntity<PageResponse<CitySummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cityService.list(page, size));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CityResponse> detail(@PathVariable String slug) {
        return ResponseEntity.ok(cityService.getBySlug(slug));
    }
}
