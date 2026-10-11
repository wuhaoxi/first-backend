package com.first.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.first.app.dto.WeatherForecastResponse;
import com.first.app.dto.WeatherHistoryResponse;
import com.first.app.dto.WeatherResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.exception.UpstreamUnavailableException;
import com.first.app.integration.OpenMeteoClient;
import com.first.app.integration.TravelProviderException;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * The single weather implementation behind the REST endpoints and the MCP {@code get_weather}
 * tool: it resolves coordinates from exactly one location selector (or takes coordinates
 * directly), serves Redis through the shared generic cache-aside helper, and maps provider
 * outcomes to domain exceptions — input rejections to {@link InvalidRequestException},
 * availability failures to {@link UpstreamUnavailableException}.
 *
 * <p>The cached value is the surface-neutral {@link WeatherForecastResponse}; the REST layer
 * overlays {@code locationName} and the selector echo after the cache read, so an entry
 * populated by the MCP surface still yields a fully named REST response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelWeatherService {

    private static final TypeReference<WeatherForecastResponse> PAYLOAD_TYPE =
            new TypeReference<>() {
            };

    private static final Duration CACHE_TTL = Duration.ofMinutes(60);

    private final CityRepository cityRepository;
    private final AttractionRepository attractionRepository;
    private final OpenMeteoClient openMeteoClient;
    private final RankingCacheService cache;

    public WeatherResponse getCurrent(String citySlug, String attractionSlug) {
        Location location = resolve(citySlug, attractionSlug);
        WeatherForecastResponse payload = cachedPayload(location, 1, 0);
        return WeatherResponse.from(payload, location.name(), citySlug, attractionSlug);
    }

    public WeatherForecastResponse getForecast(String citySlug, String attractionSlug, Integer days) {
        Location location = resolve(citySlug, attractionSlug);
        int window = days == null ? 7 : days;
        return cachedPayload(location, window, 0)
                .withLocation(location.name(), citySlug, attractionSlug);
    }

    public WeatherHistoryResponse getHistory(String citySlug, String attractionSlug, Integer days) {
        Location location = resolve(citySlug, attractionSlug);
        int window = days == null ? 7 : days;
        WeatherForecastResponse payload = cachedPayload(location, 1, window);
        return WeatherHistoryResponse.from(payload, window, location.name(), citySlug, attractionSlug);
    }

    /** MCP path: coordinates in, surface-neutral payload out (no location overlay). */
    public WeatherForecastResponse getWeather(double latitude, double longitude, int forecastDays,
            int pastDays) {
        return cachedPayload(new Location(latitude, longitude, null), forecastDays, pastDays);
    }

    private WeatherForecastResponse cachedPayload(Location location, int forecastDays, int pastDays) {
        String key = "travel:weather:" + location.latitude() + ":" + location.longitude()
                + ":f" + forecastDays + ":p" + pastDays;
        try {
            return cache.getOrLoad(key, CACHE_TTL, PAYLOAD_TYPE,
                    () -> WeatherForecastResponse.from(
                            openMeteoClient.fetchWeather(location.latitude(), location.longitude(),
                                    forecastDays, pastDays),
                            location.latitude(), location.longitude(), forecastDays));
        } catch (TravelProviderException e) {
            log.warn("Weather provider failure for {}: {}", key, e.reason());
            if (e.kind() == TravelProviderException.Kind.INPUT_REJECTION) {
                throw new InvalidRequestException(e.reason());
            }
            throw new UpstreamUnavailableException(e.reason());
        }
    }

    private Location resolve(String citySlug, String attractionSlug) {
        boolean hasCity = citySlug != null && !citySlug.isBlank();
        boolean hasAttraction = attractionSlug != null && !attractionSlug.isBlank();
        if (hasCity == hasAttraction) {
            throw new InvalidRequestException(
                    "exactly one of citySlug or attractionSlug must be supplied", "citySlug",
                    "attractionSlug");
        }
        if (hasCity) {
            City city = cityRepository.findBySlugAndStatus(citySlug, CityStatus.PUBLISHED)
                    .orElseThrow(() -> new ResourceNotFoundException("city not found: " + citySlug));
            if (city.getLatitude() == null || city.getLongitude() == null) {
                throw new InvalidRequestException("city " + citySlug + " has no coordinates configured");
            }
            return new Location(city.getLatitude(), city.getLongitude(), city.getName());
        }
        Attraction attraction = attractionRepository
                .findBySlugAndStatus(attractionSlug, AttractionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("attraction not found: " + attractionSlug));
        if (attraction.getLatitude() == null || attraction.getLongitude() == null) {
            throw new InvalidRequestException(
                    "attraction " + attractionSlug + " has no coordinates configured");
        }
        return new Location(attraction.getLatitude(), attraction.getLongitude(), attraction.getName());
    }

    private record Location(double latitude, double longitude, String name) {
    }
}
