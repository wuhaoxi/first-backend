package com.first.app.dto;

import com.first.app.integration.OpenMeteoClient;
import com.first.app.integration.TravelProviderException;

import java.util.List;

/**
 * One daily forecast entry in canonical units (°C, mm, km/h, %); {@code weatherCode} is the
 * provider's WMO code passed through unchanged.
 */
public record WeatherDailyResponse(
        String date,
        double temperatureMaxC,
        double temperatureMinC,
        double precipitationSumMm,
        double precipitationProbabilityPercent,
        double windSpeedMaxKmh,
        int weatherCode) {

    public static WeatherDailyResponse from(OpenMeteoClient.DailySeries series, int index) {
        String date = at(series.time(), index);
        Double temperatureMax = at(series.temperature2mMax(), index);
        Double temperatureMin = at(series.temperature2mMin(), index);
        Double precipitationSum = at(series.precipitationSum(), index);
        Double precipitationProbability = at(series.precipitationProbabilityMax(), index);
        Double windSpeedMax = at(series.windSpeed10mMax(), index);
        Integer weatherCode = at(series.weatherCode(), index);
        if (date == null || temperatureMax == null || temperatureMin == null
                || precipitationSum == null || precipitationProbability == null
                || windSpeedMax == null || weatherCode == null) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    "malformed Open-Meteo response: daily arrays are not aligned");
        }
        return new WeatherDailyResponse(date, temperatureMax, temperatureMin, precipitationSum,
                precipitationProbability, windSpeedMax, weatherCode);
    }

    private static <T> T at(List<T> values, int index) {
        return values == null || index >= values.size() ? null : values.get(index);
    }
}
