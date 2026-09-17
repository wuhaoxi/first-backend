package com.first.app.controller;

import com.first.app.dto.AttractionResponse;
import com.first.app.dto.AttractionSummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.service.AttractionInteractionEnricher;
import com.first.app.service.AttractionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {

    private final AttractionService attractionService;
    private final AttractionInteractionEnricher attractionInteractionEnricher;

    @GetMapping
    public ResponseEntity<PageResponse<AttractionSummaryResponse>> list(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(attractionService.list(city, category, sort, page, size));
    }

    @GetMapping("/popular")
    public ResponseEntity<List<AttractionSummaryResponse>> popular(
            @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(attractionService.popular(limit));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<AttractionResponse> detail(@PathVariable String slug,
                                                     HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        AttractionResponse response = attractionService.getBySlug(slug);
        attractionInteractionEnricher.enrich(response, userId);
        return ResponseEntity.ok(response);
    }
}
