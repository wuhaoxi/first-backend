package com.first.app.controller;

import com.first.app.dto.WeatherForecastResponse;
import com.first.app.dto.WeatherHistoryResponse;
import com.first.app.dto.WeatherResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.service.TravelWeatherService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the shared weather service — thin delegation only: location resolution,
 * provider mapping, and caching live in {@link TravelWeatherService}. The {@code days} window is
 * the one request-surface constraint enforced here (forecast 3–7, history 7–30), mirroring the
 * MCP adapter's own argument validation.
 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final TravelWeatherService travelWeatherService;

    @Operation(summary = "Current weather conditions for a city or attraction")
    @GetMapping("/current")
    public ResponseEntity<WeatherResponse> current(
            @RequestParam(required = false) String citySlug,
            @RequestParam(required = false) String attractionSlug) {
        return ResponseEntity.ok(travelWeatherService.getCurrent(citySlug, attractionSlug));
    }

    @Operation(summary = "Daily forecast for a city or attraction (3-7 days, default 7)")
    @GetMapping("/forecast")
    public ResponseEntity<WeatherForecastResponse> forecast(
            @RequestParam(required = false) String citySlug,
            @RequestParam(required = false) String attractionSlug,
            @RequestParam(required = false) String days) {
        Integer window = parseDays(days, 3, 7);
        return ResponseEntity.ok(
                travelWeatherService.getForecast(citySlug, attractionSlug, window));
    }

    @Operation(summary = "Recent daily observations for a city or attraction (7-30 days, default 7)")
    @GetMapping("/history")
    public ResponseEntity<WeatherHistoryResponse> history(
            @RequestParam(required = false) String citySlug,
            @RequestParam(required = false) String attractionSlug,
            @RequestParam(required = false) String days) {
        Integer window = parseDays(days, 7, 30);
        return ResponseEntity.ok(
                travelWeatherService.getHistory(citySlug, attractionSlug, window));
    }

    /** Parses the optional window and bounds-checks it; {@code null} delegates the default to the service. */
    private Integer parseDays(String raw, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("days must be an integer (got \"" + raw + "\")", "days");
        }
        if (value < min || value > max) {
            throw new InvalidRequestException(
                    "days must be between " + min + " and " + max + " (got " + value + ")", "days");
        }
        return value;
    }
}
