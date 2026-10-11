package com.first.app.dto;

import com.first.app.integration.OpenMeteoClient;
import com.first.app.integration.TravelProviderException;

/**
 * Current conditions for one resolved location. Built from the surface-neutral cache payload
 * with the REST overlay (location name and selector echo) applied — never itself cached.
 */
public record WeatherResponse(
        String citySlug,
        String attractionSlug,
        String locationName,
        double latitude,
        double longitude,
        String timezone,
        String observedAt,
        Current current,
        String attribution) {

    public static final String ATTRIBUTION = "Weather data by Open-Meteo (CC BY 4.0)";

    public static WeatherResponse from(WeatherForecastResponse payload, String locationName,
            String citySlug, String attractionSlug) {
        return new WeatherResponse(citySlug, attractionSlug, locationName, payload.latitude(),
                payload.longitude(), payload.timezone(), payload.observedAt(), payload.current(),
                ATTRIBUTION);
    }

    /** One current-conditions observation: °C, %, mm, km/h, degrees, WMO code. */
    public record Current(
            double temperatureC,
            double apparentTemperatureC,
            double humidityPercent,
            double precipitationMm,
            double windSpeedKmh,
            double windDirectionDegrees,
            int weatherCode) {

        public static Current from(OpenMeteoClient.CurrentSnapshot snapshot) {
            if (snapshot.temperature2m() == null || snapshot.apparentTemperature() == null
                    || snapshot.relativeHumidity2m() == null || snapshot.precipitation() == null
                    || snapshot.windSpeed10m() == null || snapshot.windDirection10m() == null
                    || snapshot.weatherCode() == null) {
                throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                        "malformed Open-Meteo response: current conditions are incomplete");
            }
            return new Current(snapshot.temperature2m(), snapshot.apparentTemperature(),
                    snapshot.relativeHumidity2m(), snapshot.precipitation(), snapshot.windSpeed10m(),
                    snapshot.windDirection10m(), snapshot.weatherCode());
        }
    }
}
