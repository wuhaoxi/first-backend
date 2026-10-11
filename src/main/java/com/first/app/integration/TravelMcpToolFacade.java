package com.first.app.integration;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * The annotated MCP server shell: declares {@code get_weather} and {@code get_exchange_rate} for
 * the Spring AI stateless MCP server starter and delegates each call to {@link TravelMcpTools},
 * returning its {@link CallToolResult} unchanged.
 *
 * <p>Tool names, descriptions, parameter descriptions, and requiredness mirror the frozen Phase 1
 * contracts; the validation messages and payloads stay byte-exact because {@link TravelMcpTools}
 * remains the validation authority. Domain outcomes come back as tool results (never thrown), so
 * the starter's exception converter cannot rewrite the frozen {@code isError} envelopes.</p>
 */
@Component
public class TravelMcpToolFacade {

    private static final String WEATHER_TOOL_DESCRIPTION =
            "Current conditions, a daily forecast, and recent daily observations for a coordinate, "
                    + "from Open-Meteo. Temperatures in °C, precipitation in mm, wind in km/h.";

    private static final String EXCHANGE_RATE_TOOL_DESCRIPTION =
            "Latest exchange rate(s) for a currency pair (ExchangeRate-API), or a daily series "
                    + "for a date range (Frankfurter, ECB reference rates, business days only).";

    private final TravelMcpTools travelMcpTools;

    public TravelMcpToolFacade(TravelMcpTools travelMcpTools) {
        this.travelMcpTools = travelMcpTools;
    }

    /** MCP {@code get_weather}: validates then delegates; the result is returned unchanged. */
    @McpTool(name = "get_weather", description = WEATHER_TOOL_DESCRIPTION)
    public CallToolResult getWeather(
            @McpToolParam(description = "Latitude in decimal degrees (-90..90).", required = true)
            Double latitude,
            @McpToolParam(description = "Longitude in decimal degrees (-180..180).", required = true)
            Double longitude,
            @McpToolParam(description = "Number of forecast days (1..7). Defaults to 7.",
                    required = false)
            Integer forecastDays,
            @McpToolParam(description = "Number of recent observed days (0..30). Defaults to 0.",
                    required = false)
            Integer pastDays) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("latitude", latitude);
        arguments.put("longitude", longitude);
        arguments.put("forecastDays", forecastDays);
        arguments.put("pastDays", pastDays);
        return travelMcpTools.getWeather(arguments);
    }

    /** MCP {@code get_exchange_rate}: validates then delegates; the result is returned unchanged. */
    @McpTool(name = "get_exchange_rate", description = EXCHANGE_RATE_TOOL_DESCRIPTION)
    public CallToolResult getExchangeRate(
            @McpToolParam(description = "Base currency as a 3-letter ISO-4217 code (e.g. CNY).",
                    required = true)
            String base,
            @McpToolParam(
                    description = "Quote currency, or a comma-separated list of them, as 3-letter "
                            + "ISO-4217 codes (e.g. USD,EUR).",
                    required = true)
            String quote,
            @McpToolParam(description = "Series start date (ISO YYYY-MM-DD); provide together "
                    + "with toDate.", required = false)
            String fromDate,
            @McpToolParam(description = "Series end date (ISO YYYY-MM-DD); provide together "
                    + "with fromDate.", required = false)
            String toDate) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("base", base);
        arguments.put("quote", quote);
        arguments.put("fromDate", fromDate);
        arguments.put("toDate", toDate);
        return travelMcpTools.getExchangeRate(arguments);
    }
}
