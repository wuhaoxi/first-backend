package com.first.app.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.ExchangeRateHistoryResponse;
import com.first.app.dto.ExchangeRateLatestResponse;
import com.first.app.dto.WeatherDailyResponse;
import com.first.app.dto.WeatherForecastResponse;
import com.first.app.dto.WeatherResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.UpstreamUnavailableException;
import com.first.app.service.ExchangeRateService;
import com.first.app.service.TravelWeatherService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The MCP tool adapters: thin clients of the shared travel services — validation → service call →
 * frozen result mapping. No provider logic, cache access, or repository access lives here, and no
 * client class or {@code RestClient} is referenced.
 *
 * <p>Validation mirrors the Phase 1 Node server's rules and messages byte for byte (strict codes,
 * no trimming) and runs before any service call. Results are one JSON text content block; domain
 * exceptions become structured tool errors ({@code isError} semantics) with the frozen envelope
 * {@code { "error": { "kind": "input-rejection" | "upstream-unavailable", "reason": ... } }} — an
 * unexpected exception is mapped to the availability kind rather than escaping the server.
 */
@Component
public class TravelMcpTools {

    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final TravelWeatherService weatherService;
    private final ExchangeRateService exchangeRateService;
    private final ObjectMapper objectMapper;

    public TravelMcpTools(TravelWeatherService weatherService,
            ExchangeRateService exchangeRateService, ObjectMapper objectMapper) {
        this.weatherService = weatherService;
        this.exchangeRateService = exchangeRateService;
        this.objectMapper = objectMapper;
    }

    public CallToolResult getWeather(Map<String, ?> arguments) {
        try {
            double latitude = numberInRange(arguments.get("latitude"), "latitude", -90, 90);
            double longitude = numberInRange(arguments.get("longitude"), "longitude", -180, 180);
            int forecastDays = integerInRange(arguments.get("forecastDays"), "forecastDays", 1, 7, 7);
            int pastDays = integerInRange(arguments.get("pastDays"), "pastDays", 0, 30, 0);

            WeatherForecastResponse forecast =
                    weatherService.getWeather(latitude, longitude, forecastDays, pastDays);
            // Frozen MCP shape: surface-neutral payload fields beyond the Phase 1 contract are
            // deliberately not projected.
            return toolResult(new WeatherToolResult(forecast.timezone(), forecast.observedAt(),
                    forecast.current(), forecast.daily(), forecast.attribution()));
        } catch (ArgumentRejection e) {
            return toolError("input-rejection", e.getMessage());
        } catch (InvalidRequestException e) {
            return toolError("input-rejection", e.getMessage());
        } catch (UpstreamUnavailableException e) {
            return toolError("upstream-unavailable", e.getMessage());
        } catch (RuntimeException e) {
            return toolError("upstream-unavailable", e.getMessage());
        }
    }

    public CallToolResult getExchangeRate(Map<String, ?> arguments) {
        try {
            String base = currency(arguments.get("base"), "base");
            String quote = currencyList(arguments.get("quote"), "quote");
            boolean hasFrom = arguments.get("fromDate") != null;
            boolean hasTo = arguments.get("toDate") != null;
            if (hasFrom != hasTo) {
                throw new ArgumentRejection("fromDate and toDate must be provided together");
            }

            if (hasFrom) {
                if (quote.contains(",")) {
                    throw new ArgumentRejection(
                            "quote must be a single 3-letter ISO-4217 code when fromDate/toDate are provided (got "
                                    + format(quote) + ")");
                }
                String fromDate = isoDate(arguments.get("fromDate"), "fromDate");
                String toDate = isoDate(arguments.get("toDate"), "toDate");
                ExchangeRateHistoryResponse history =
                        exchangeRateService.getHistory(base, quote, fromDate, toDate);
                // Frozen series shape: the REST-only fields (quote, requestedDays, returnedDays)
                // are deliberately not projected.
                return toolResult(new ExchangeSeriesToolResult(history.base(), history.fromDate(),
                        history.toDate(), history.series(), history.attribution()));
            }

            ExchangeRateLatestResponse latest = exchangeRateService.getLatest(base, quote);
            return toolResult(new ExchangeLatestToolResult(latest.base(), latest.asOfDate(),
                    latest.quotes(), latest.attribution()));
        } catch (ArgumentRejection e) {
            return toolError("input-rejection", e.getMessage());
        } catch (InvalidRequestException e) {
            return toolError("input-rejection", e.getMessage());
        } catch (UpstreamUnavailableException e) {
            return toolError("upstream-unavailable", e.getMessage());
        } catch (RuntimeException e) {
            return toolError("upstream-unavailable", e.getMessage());
        }
    }

    private CallToolResult toolResult(Object value) {
        return CallToolResult.builder().addTextContent(json(value)).build();
    }

    private CallToolResult toolError(String kind, String reason) {
        return CallToolResult.builder()
                .addTextContent(json(new ToolErrorEnvelope(new ToolError(kind, reason))))
                .isError(true)
                .build();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Serializing our own records cannot realistically fail; never let it escape a tool.
            throw new IllegalStateException("tool result serialization failed", e);
        }
    }

    private static double numberInRange(Object raw, String name, int min, int max) {
        if (raw == null) {
            throw new ArgumentRejection(name + " is required");
        }
        if (!(raw instanceof Number number) || !isFinite(number)) {
            throw new ArgumentRejection(name + " must be a number (got " + format(raw) + ")");
        }
        double value = number.doubleValue();
        if (value < min || value > max) {
            throw new ArgumentRejection(
                    name + " must be between " + min + " and " + max + " (got " + format(raw) + ")");
        }
        return value;
    }

    private static int integerInRange(Object raw, String name, int min, int max, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number) || !isFinite(number) || !isWhole(number)) {
            throw new ArgumentRejection(name + " must be an integer (got " + format(raw) + ")");
        }
        int value = number.intValue();
        if (value < min || value > max) {
            throw new ArgumentRejection(
                    name + " must be between " + min + " and " + max + " (got " + format(raw) + ")");
        }
        return value;
    }

    private static String isoDate(Object raw, String name) {
        if (!(raw instanceof String value) || !ISO_DATE.matcher(value).matches()) {
            throw new ArgumentRejection(
                    name + " must be an ISO date (YYYY-MM-DD) (got " + format(raw) + ")");
        }
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ArgumentRejection(
                    name + " must be an ISO date (YYYY-MM-DD) (got " + format(raw) + ")");
        }
        return value;
    }

    private static String currency(Object raw, String name) {
        if (raw == null) {
            throw new ArgumentRejection(name + " is required");
        }
        if (!(raw instanceof String value) || !ISO_4217.matcher(value).matches()) {
            throw new ArgumentRejection(
                    name + " must be a 3-letter ISO-4217 code (got " + format(raw) + ")");
        }
        return value;
    }

    private static String currencyList(Object raw, String name) {
        if (raw == null) {
            throw new ArgumentRejection(name + " is required");
        }
        if (!(raw instanceof String value) || Arrays.stream(value.split(","))
                .anyMatch(part -> !ISO_4217.matcher(part).matches())) {
            throw new ArgumentRejection(
                    name + " must be a 3-letter ISO-4217 code or a comma-separated list of them (got "
                            + format(raw) + ")");
        }
        return value;
    }

    private static boolean isFinite(Number number) {
        double value = number.doubleValue();
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isWhole(Number number) {
        double value = number.doubleValue();
        return value == Math.rint(value);
    }

    /** Emulates {@code JSON.stringify} for the scalar types the arguments can carry. */
    private static String format(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof String text) {
            return "\"" + text + "\"";
        }
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (isFinite(number) && isWhole(number)
                    && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return String.valueOf((long) value);
            }
            return String.valueOf(raw);
        }
        if (raw instanceof Boolean flag) {
            return String.valueOf(flag);
        }
        return "\"" + raw + "\"";
    }

    /** Internal carrier so validation aborts on the first offending argument. */
    private static final class ArgumentRejection extends RuntimeException {

        ArgumentRejection(String reason) {
            super(reason);
        }
    }

    /** Frozen MCP result shapes — the Phase 1 parity contract, not the REST DTO superset. */
    public record WeatherToolResult(
            String timezone,
            String observedAt,
            WeatherResponse.Current current,
            List<WeatherDailyResponse> daily,
            String attribution) {
    }

    public record ExchangeLatestToolResult(
            String base,
            String asOfDate,
            List<ExchangeRateLatestResponse.Quote> quotes,
            String attribution) {
    }

    public record ExchangeSeriesToolResult(
            String base,
            String fromDate,
            String toDate,
            List<ExchangeRateHistoryResponse.SeriesPoint> series,
            String attribution) {
    }

    private record ToolError(String kind, String reason) {
    }

    private record ToolErrorEnvelope(ToolError error) {
    }
}
