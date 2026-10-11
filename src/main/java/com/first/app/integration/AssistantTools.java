package com.first.app.integration;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI {@code @Tool} adapter for the assistant (work item 3.x of add-ai-assistant): the
 * in-process twin of {@link TravelMcpToolFacade}, assembled exactly like it and delegating to the
 * same validation authority.
 *
 * <p>Arguments are forwarded with nulls included so {@link TravelMcpTools} stays the only
 * validation layer; domain outcomes (including the frozen {@code { "error": { "kind", "reason" } }}
 * envelopes) come back as tool results, never thrown. Each result's first text content block is
 * returned unchanged — the JSON string the model sees.
 */
@Component
public class AssistantTools {

    private static final String WEATHER_TOOL_DESCRIPTION =
            "Current conditions, a daily forecast, and recent daily observations for a coordinate, "
                    + "from Open-Meteo. Temperatures in °C, precipitation in mm, wind in km/h.";

    private static final String EXCHANGE_RATE_TOOL_DESCRIPTION =
            "Latest exchange rate(s) for a currency pair (ExchangeRate-API), or a daily series "
                    + "for a date range (Frankfurter, ECB reference rates, business days only).";

    private final TravelMcpTools travelMcpTools;

    public AssistantTools(TravelMcpTools travelMcpTools) {
        this.travelMcpTools = travelMcpTools;
    }

    /** Tool {@code get_weather}: validates then delegates; the first text block is returned as-is. */
    @Tool(name = "get_weather", description = WEATHER_TOOL_DESCRIPTION)
    public String getWeather(
            @ToolParam(description = "Latitude in decimal degrees (-90..90).", required = true)
            Double latitude,
            @ToolParam(description = "Longitude in decimal degrees (-180..180).", required = true)
            Double longitude,
            @ToolParam(description = "Number of forecast days (1..7). Defaults to 7.",
                    required = false)
            Integer forecastDays,
            @ToolParam(description = "Number of recent observed days (0..30). Defaults to 0.",
                    required = false)
            Integer pastDays) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("latitude", latitude);
        arguments.put("longitude", longitude);
        arguments.put("forecastDays", forecastDays);
        arguments.put("pastDays", pastDays);
        return firstText(travelMcpTools.getWeather(arguments));
    }

    /** Tool {@code get_exchange_rate}: validates then delegates; the first text block is returned as-is. */
    @Tool(name = "get_exchange_rate", description = EXCHANGE_RATE_TOOL_DESCRIPTION)
    public String getExchangeRate(
            @ToolParam(description = "Base currency as a 3-letter ISO-4217 code (e.g. CNY).",
                    required = true)
            String base,
            @ToolParam(
                    description = "Quote currency, or a comma-separated list of them, as 3-letter "
                            + "ISO-4217 codes (e.g. USD,EUR).",
                    required = true)
            String quote,
            @ToolParam(description = "Series start date (ISO YYYY-MM-DD); provide together "
                    + "with toDate.", required = false)
            String fromDate,
            @ToolParam(description = "Series end date (ISO YYYY-MM-DD); provide together "
                    + "with fromDate.", required = false)
            String toDate) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("base", base);
        arguments.put("quote", quote);
        arguments.put("fromDate", fromDate);
        arguments.put("toDate", toDate);
        return firstText(travelMcpTools.getExchangeRate(arguments));
    }

    private static String firstText(CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .findFirst()
                .orElse("");
    }
}
