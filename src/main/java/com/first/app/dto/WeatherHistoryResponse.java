package com.first.app.dto;

import java.util.List;

/**
 * Recent daily observations for one resolved location, projected from the cached forecast
 * payload; {@code days} is the requested past-days window. Observation entries drop the
 * forecast-only probability and WMO code fields.
 */
public record WeatherHistoryResponse(
        String citySlug,
        String attractionSlug,
        String locationName,
        double latitude,
        double longitude,
        int days,
        String timezone,
        List<Day> daily,
        String attribution) {

    public static WeatherHistoryResponse from(WeatherForecastResponse payload, int days,
            String locationName, String citySlug, String attractionSlug) {
        return new WeatherHistoryResponse(citySlug, attractionSlug, locationName, payload.latitude(),
                payload.longitude(), days, payload.timezone(),
                payload.daily().stream().map(Day::from).toList(), WeatherResponse.ATTRIBUTION);
    }

    /** One observation day. */
    public record Day(
            String date,
            double temperatureMaxC,
            double temperatureMinC,
            double precipitationSumMm,
            double windSpeedMaxKmh) {

        static Day from(WeatherDailyResponse entry) {
            return new Day(entry.date(), entry.temperatureMaxC(), entry.temperatureMinC(),
                    entry.precipitationSumMm(), entry.windSpeedMaxKmh());
        }
    }
}
