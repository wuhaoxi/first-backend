package com.first.app.integration;

import com.first.app.service.KnowledgeSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI {@code @Tool} adapter exposing the local knowledge base to the assistant. Delegates
 * each call to {@link KnowledgeSearchService}, which never throws: every outcome (labeled
 * results, no-results, unavailability) arrives as plain text, so the SSE chat stream always stays
 * alive. The adapter adds no logic of its own.
 */
@Component
public class KnowledgeTools {

    private static final String KNOWLEDGE_TOOL_DESCRIPTION =
            "Search WanderChina's published site content — attraction guides, city overviews, and "
                    + "travel posts — for passages relevant to a question. Returns labeled "
                    + "plain-text passages, or a notice when nothing relevant exists / the "
                    + "knowledge base is unavailable.";

    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeTools(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    /** Tool {@code search_knowledge_base}: retrieves site content; returns the service text as-is. */
    @Tool(name = "search_knowledge_base", description = KNOWLEDGE_TOOL_DESCRIPTION)
    public String searchKnowledgeBase(
            @ToolParam(description = "The user's question about site content (attractions, "
                    + "cities, or travel posts), in their own language.", required = true)
            String question) {
        return knowledgeSearchService.search(question);
    }
}
