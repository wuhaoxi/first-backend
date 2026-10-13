package com.first.app.controller;

import com.first.app.dto.ReindexResponse;
import com.first.app.service.KnowledgeIndexService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin HTTP shell of the knowledge-base maintenance endpoint: rebuilds the vector index from
 * PUBLISHED content and returns the per-source counts. Authentication is the existing cookie
 * {@code /api/**} rule; a store outage surfaces as 503 through {@code GlobalExceptionHandler}
 * (never a fake-success 200 with zero counts).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeIndexService knowledgeIndexService;

    @PostMapping("/knowledge-base/reindex")
    @Operation(summary = "Rebuild the knowledge-base index from published attractions, cities, and posts")
    public ReindexResponse reindex() {
        return knowledgeIndexService.reindexAll();
    }
}
