package com.first.app.integration;

import com.first.app.service.AttractionSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI {@code @Tool} adapter exposing structured attraction search to the assistant.
 * Delegates each call to {@link AttractionSearchService}, which never throws: results arrive as
 * compact JSON rows or plain-text notices (no match / unavailability / input-rejection envelope),
 * so the SSE chat stream always stays alive. The adapter adds no logic of its own.
 */
@Component
public class AttractionTools {

    private static final String ATTRACTION_TOOL_DESCRIPTION =
            "Search WanderChina's published attractions for structured facts — ticket prices, "
                    + "opening hours, booking requirements, addresses, and ratings. Filter "
                    + "optionally by city (Chinese name, English name, or slug), category, or a "
                    + "name keyword. Returns compact JSON rows, or a notice when nothing matches / "
                    + "the data is unavailable.";

    private final AttractionSearchService attractionSearchService;

    public AttractionTools(AttractionSearchService attractionSearchService) {
        this.attractionSearchService = attractionSearchService;
    }

    /** Tool {@code search_attractions}: retrieves published attractions; returns the service text as-is. */
    @Tool(name = "search_attractions", description = ATTRACTION_TOOL_DESCRIPTION)
    public String searchAttractions(
            @ToolParam(description = "City filter: Chinese name (e.g. 杭州), English name "
                    + "(e.g. Hangzhou), or slug (e.g. hangzhou). Omit to search all cities.",
                    required = false)
            String city,
            @ToolParam(description = "Category filter. One of HISTORICAL_SITE, TEMPLE, MUSEUM, "
                    + "NATURE, MODERN_LANDMARK, STREET_DISTRICT. Omit for all categories.",
                    required = false)
            String category,
            @ToolParam(description = "Case-insensitive keyword matched against the attraction's "
                    + "Chinese and English name. Omit for no keyword filter.", required = false)
            String keyword,
            @ToolParam(description = "Maximum number of rows to return, between 1 and 10 "
                    + "(default 5).", required = false)
            Integer limit) {
        return attractionSearchService.search(city, category, keyword, limit);
    }
}
