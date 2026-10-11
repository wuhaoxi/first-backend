package com.first.app.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * The only backend component that issues HTTP to the Open-Meteo forecast API.
 *
 * <p>Provider outcomes are mapped here to {@link TravelProviderException}: any failure — non-2xx,
 * timeout, or a malformed payload — is an availability failure, because request inputs are
 * validated before the call. The client never touches the request factory: the timeout budget is
 * applied to the injected builder, which keeps this class bindable to {@code MockRestServiceServer}.
 */
@Component
public class OpenMeteoClient {

    private static final String FORECAST_PATH = "/v1/forecast";

    private static final List<String> CURRENT_FIELDS = List.of(
            "temperature_2m", "apparent_temperature", "relative_humidity_2m", "precipitation",
            "wind_speed_10m", "wind_direction_10m", "weather_code");

    private static final List<String> DAILY_FIELDS = List.of(
            "temperature_2m_max", "temperature_2m_min", "precipitation_sum",
            "precipitation_probability_max", "wind_speed_10m_max", "weather_code");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenMeteoClient(RestClient.Builder builder, TravelProviderProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(properties.openMeteoBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    public WeatherPayload fetchWeather(double latitude, double longitude, int forecastDays, int pastDays) {
        WeatherPayload payload;
        try {
            payload = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(FORECAST_PATH)
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", String.join(",", CURRENT_FIELDS))
                            .queryParam("daily", String.join(",", DAILY_FIELDS))
                            .queryParam("forecast_days", forecastDays)
                            .queryParam("past_days", pastDays)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(WeatherPayload.class);
        } catch (RestClientResponseException e) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE, reasonFrom(e));
        } catch (RestClientException e) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    "malformed Open-Meteo response: " + e.getMessage());
        }
        return requireValid(payload);
    }

    private WeatherPayload requireValid(WeatherPayload payload) {
        if (payload == null || payload.timezone() == null || payload.current() == null
                || payload.daily() == null || payload.daily().time() == null) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    "malformed Open-Meteo response: required fields missing");
        }
        return payload;
    }

    private String reasonFrom(RestClientResponseException e) {
        try {
            JsonNode reason = objectMapper.readTree(e.getResponseBodyAsString()).get("reason");
            if (reason != null && !reason.isNull() && !reason.asText().isBlank()) {
                return reason.asText();
            }
        } catch (JsonProcessingException ignored) {
            // Not the documented error shape — fall back to the status-based message below.
        }
        return "Open-Meteo request failed with status " + e.getStatusCode().value();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherPayload(String timezone, CurrentSnapshot current, DailySeries daily) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentSnapshot(
            String time,
            @JsonProperty("temperature_2m") Double temperature2m,
            @JsonProperty("apparent_temperature") Double apparentTemperature,
            @JsonProperty("relative_humidity_2m") Double relativeHumidity2m,
            Double precipitation,
            @JsonProperty("wind_speed_10m") Double windSpeed10m,
            @JsonProperty("wind_direction_10m") Double windDirection10m,
            @JsonProperty("weather_code") Integer weatherCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailySeries(
            List<String> time,
            @JsonProperty("temperature_2m_max") List<Double> temperature2mMax,
            @JsonProperty("temperature_2m_min") List<Double> temperature2mMin,
            @JsonProperty("precipitation_sum") List<Double> precipitationSum,
            @JsonProperty("precipitation_probability_max") List<Double> precipitationProbabilityMax,
            @JsonProperty("wind_speed_10m_max") List<Double> windSpeed10mMax,
            @JsonProperty("weather_code") List<Integer> weatherCode) {
    }
}
