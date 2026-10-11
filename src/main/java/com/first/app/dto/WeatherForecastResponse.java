package com.first.app.dto;

import com.first.app.integration.OpenMeteoClient;
import com.first.app.integration.TravelProviderException;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The surface-neutral weather payload: the value stored in Redis under
 * {@code travel:weather:{latitude}:{longitude}:f{forecastDays}:p{pastDays}} and returned to both
 * the REST forecast endpoint and the MCP {@code get_weather} tool. Cached entries carry no slug
 * or name echo; {@link #withLocation} applies the REST overlay after the cache read.
 */
public record WeatherForecastResponse(
        String citySlug,
        String attractionSlug,
        String locationName,
        double latitude,
        double longitude,
        int days,
        String timezone,
        String observedAt,
        WeatherResponse.Current current,
        List<WeatherDailyResponse> daily,
        String attribution) {

    public static WeatherForecastResponse from(OpenMeteoClient.WeatherPayload payload,
            double latitude, double longitude, int forecastDays) {
        String observedAt = payload.current() == null ? null : payload.current().time();
        if (observedAt == null || observedAt.isBlank()) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    "malformed Open-Meteo response: current conditions are incomplete");
        }
        return new WeatherForecastResponse(null, null, null, latitude, longitude, forecastDays,
                payload.timezone(), observedAt, WeatherResponse.Current.from(payload.current()),
                mapDaily(payload), WeatherResponse.ATTRIBUTION);
    }

    /** Applies the REST overlay (location name and selector echo) to a cached payload. */
    public WeatherForecastResponse withLocation(String locationName, String citySlug,
            String attractionSlug) {
        return new WeatherForecastResponse(citySlug, attractionSlug, locationName, latitude,
                longitude, days, timezone, observedAt, current, daily, attribution);
    }

    private static List<WeatherDailyResponse> mapDaily(OpenMeteoClient.WeatherPayload payload) {
        if (payload.daily() == null || payload.daily().time() == null) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    "malformed Open-Meteo response: daily arrays are not aligned");
        }
        return IntStream.range(0, payload.daily().time().size())
                .mapToObj(index -> WeatherDailyResponse.from(payload.daily(), index))
                .toList();
    }
}
