package com.first.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.integration.ExchangeRateApiClient;
import com.first.app.integration.FrankfurterClient;
import com.first.app.integration.OpenMeteoClient;
import com.first.app.integration.TravelProviderException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the serialized shape of the six travel DTOs: camelCase field names, the attribution
 * literals, the daily/series item fields, and the cache payload's ObjectMapper round-trip.
 * Entities never cross this boundary — no {@code City} field may ever appear.
 */
class TravelDtoTest {

    private static final String WEATHER_ATTRIBUTION = "Weather data by Open-Meteo (CC BY 4.0)";
    private static final String LATEST_ATTRIBUTION = "Rates By Exchange Rate API";
    private static final String SERIES_ATTRIBUTION = "Daily reference rates via Frankfurter";

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode jsonOf(Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(value));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static OpenMeteoClient.WeatherPayload samplePayload() {
        return new OpenMeteoClient.WeatherPayload("Asia/Shanghai", sampleCurrent(), sampleDaily());
    }

    private static OpenMeteoClient.CurrentSnapshot sampleCurrent() {
        return new OpenMeteoClient.CurrentSnapshot(
                "2026-10-05T10:00", 21.4, 20.1, 65.0, 0.0, 8.5, 180.0, 2);
    }

    private static OpenMeteoClient.DailySeries sampleDaily() {
        return new OpenMeteoClient.DailySeries(
                List.of("2026-10-04", "2026-10-05", "2026-10-06"),
                List.of(22.0, 21.5, 20.8),
                List.of(12.0, 11.4, 10.9),
                List.of(0.0, 0.6, 1.2),
                List.of(10.0, 55.0, 70.0),
                List.of(14.0, 16.5, 12.0),
                List.of(1, 61, 63));
    }

    private static WeatherForecastResponse surfaceNeutralPayload() {
        return WeatherForecastResponse.from(samplePayload(), 39.9042, 116.4074, 7);
    }

    @Test
    void weatherForecastResponse_surfaceNeutralPayload_roundTripsLosslesslyThroughObjectMapper()
            throws Exception {
        WeatherForecastResponse payload = surfaceNeutralPayload();

        WeatherForecastResponse restored = mapper.readValue(
                mapper.writeValueAsString(payload), WeatherForecastResponse.class);

        assertThat(restored).isEqualTo(payload);
        // Surface-neutral storage: no slug or name echo is cached; the REST layer overlays them.
        assertThat(restored.citySlug()).isNull();
        assertThat(restored.attractionSlug()).isNull();
        assertThat(restored.locationName()).isNull();
    }

    @Test
    void weatherForecastResponse_serializesExactCamelCaseFieldSet() throws Exception {
        JsonNode json = jsonOf(surfaceNeutralPayload().withLocation("Beijing", "beijing", null));

        assertThat(fieldNames(json)).containsExactlyInAnyOrderElementsOf(Set.of(
                "citySlug", "attractionSlug", "locationName", "latitude", "longitude", "days",
                "timezone", "observedAt", "current", "daily", "attribution"));
        assertThat(json.get("attribution").asText()).isEqualTo(WEATHER_ATTRIBUTION);
        assertThat(json.get("days").asInt()).isEqualTo(7);
        assertThat(json.get("citySlug").asText()).isEqualTo("beijing");
        assertThat(fieldNames(json.get("current"))).containsExactlyInAnyOrderElementsOf(Set.of(
                "temperatureC", "apparentTemperatureC", "humidityPercent", "precipitationMm",
                "windSpeedKmh", "windDirectionDegrees", "weatherCode"));
        assertThat(json.get("current").get("temperatureC").asDouble()).isEqualTo(21.4);
        assertThat(fieldNames(json.get("daily").get(0))).containsExactlyInAnyOrderElementsOf(Set.of(
                "date", "temperatureMaxC", "temperatureMinC", "precipitationSumMm",
                "precipitationProbabilityPercent", "windSpeedMaxKmh", "weatherCode"));
        assertThat(json.get("daily").get(0).get("date").asText()).isEqualTo("2026-10-04");
        // City entity fields never cross the API boundary.
        assertThat(json.has("status")).isFalse();
        assertThat(json.has("id")).isFalse();
    }

    @Test
    void weatherResponse_serializesExactCamelCaseFieldSet() throws Exception {
        WeatherResponse current = WeatherResponse.from(surfaceNeutralPayload(), "Beijing", "beijing", null);

        JsonNode json = jsonOf(current);

        assertThat(fieldNames(json)).containsExactlyInAnyOrderElementsOf(Set.of(
                "citySlug", "attractionSlug", "locationName", "latitude", "longitude",
                "timezone", "observedAt", "current", "attribution"));
        assertThat(json.get("attribution").asText()).isEqualTo(WEATHER_ATTRIBUTION);
        assertThat(json.get("observedAt").asText()).isEqualTo("2026-10-05T10:00");
        assertThat(fieldNames(json.get("current"))).containsExactlyInAnyOrderElementsOf(Set.of(
                "temperatureC", "apparentTemperatureC", "humidityPercent", "precipitationMm",
                "windSpeedKmh", "windDirectionDegrees", "weatherCode"));
        assertThat(json.has("status")).isFalse();
        assertThat(json.has("id")).isFalse();
    }

    @Test
    void weatherHistoryResponse_serializesFiveFieldDailyEntries() throws Exception {
        WeatherHistoryResponse history =
                WeatherHistoryResponse.from(surfaceNeutralPayload(), 7, "Beijing", "beijing", null);

        JsonNode json = jsonOf(history);

        assertThat(fieldNames(json)).containsExactlyInAnyOrderElementsOf(Set.of(
                "citySlug", "attractionSlug", "locationName", "latitude", "longitude", "days",
                "timezone", "daily", "attribution"));
        assertThat(json.get("days").asInt()).isEqualTo(7);
        assertThat(json.get("attribution").asText()).isEqualTo(WEATHER_ATTRIBUTION);
        assertThat(fieldNames(json.get("daily").get(0))).containsExactlyInAnyOrderElementsOf(Set.of(
                "date", "temperatureMaxC", "temperatureMinC", "precipitationSumMm",
                "windSpeedMaxKmh"));
        assertThat(json.get("daily").get(0).get("date").asText()).isEqualTo("2026-10-04");
    }

    @Test
    void exchangeRateLatestResponse_serializesExactCamelCaseFieldSet() throws Exception {
        ExchangeRateApiClient.LatestRates latest = new ExchangeRateApiClient.LatestRates(
                "CNY", "2026-10-05",
                List.of(new ExchangeRateApiClient.Quote("USD", 0.14924),
                        new ExchangeRateApiClient.Quote("EUR", 0.13245)));

        JsonNode json = jsonOf(ExchangeRateLatestResponse.from(latest));

        assertThat(fieldNames(json)).containsExactlyInAnyOrderElementsOf(Set.of(
                "base", "asOfDate", "quotes", "attribution"));
        assertThat(json.get("base").asText()).isEqualTo("CNY");
        assertThat(json.get("asOfDate").asText()).isEqualTo("2026-10-05");
        assertThat(json.get("attribution").asText()).isEqualTo(LATEST_ATTRIBUTION);
        assertThat(fieldNames(json.get("quotes").get(0)))
                .containsExactlyInAnyOrderElementsOf(Set.of("currency", "rate"));
        // Request order is preserved, not provider order.
        assertThat(json.get("quotes").get(0).get("currency").asText()).isEqualTo("USD");
        assertThat(json.get("quotes").get(1).get("currency").asText()).isEqualTo("EUR");
    }

    @Test
    void exchangeRateHistoryResponse_serializesDayCountsAndSeriesItems() throws Exception {
        List<FrankfurterClient.SeriesPoint> points = List.of(
                new FrankfurterClient.SeriesPoint("2026-01-02", 7.31),
                new FrankfurterClient.SeriesPoint("2026-01-05", 7.29),
                new FrankfurterClient.SeriesPoint("2026-01-06", 7.33));

        JsonNode json = jsonOf(ExchangeRateHistoryResponse.from(
                "CNY", "USD", "2026-01-01", "2026-03-31", points));

        assertThat(fieldNames(json)).containsExactlyInAnyOrderElementsOf(Set.of(
                "base", "quote", "fromDate", "toDate", "series", "requestedDays",
                "returnedDays", "attribution"));
        assertThat(json.get("attribution").asText()).isEqualTo(SERIES_ATTRIBUTION);
        // Inclusive span: 2026-01-01..2026-03-31 is 90 days; provider published 3 of them.
        assertThat(json.get("requestedDays").asInt()).isEqualTo(90);
        assertThat(json.get("returnedDays").asInt()).isEqualTo(3);
        assertThat(fieldNames(json.get("series").get(0)))
                .containsExactlyInAnyOrderElementsOf(Set.of("date", "rate"));
        assertThat(json.get("series").get(0).get("date").asText()).isEqualTo("2026-01-02");
    }

    @Test
    void weatherForecastResponse_raggedDailyColumns_throwsAvailabilityNotError() {
        OpenMeteoClient.DailySeries ragged = new OpenMeteoClient.DailySeries(
                List.of("2026-10-04", "2026-10-05", "2026-10-06"),
                List.of(22.0, 21.5),
                List.of(12.0, 11.4, 10.9),
                List.of(0.0, 0.6, 1.2),
                List.of(10.0, 55.0, 70.0),
                List.of(14.0, 16.5, 12.0),
                List.of(1, 61, 63));
        OpenMeteoClient.WeatherPayload payload =
                new OpenMeteoClient.WeatherPayload("Asia/Shanghai", sampleCurrent(), ragged);

        assertThatThrownBy(() -> WeatherForecastResponse.from(payload, 39.9042, 116.4074, 7))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("malformed");
                });
    }

    @Test
    void weatherForecastResponse_incompleteCurrentSnapshot_throwsAvailability() {
        OpenMeteoClient.CurrentSnapshot incomplete = new OpenMeteoClient.CurrentSnapshot(
                "2026-10-05T10:00", null, 20.1, 65.0, 0.0, 8.5, 180.0, 2);
        OpenMeteoClient.WeatherPayload payload =
                new OpenMeteoClient.WeatherPayload("Asia/Shanghai", incomplete, sampleDaily());

        assertThatThrownBy(() -> WeatherForecastResponse.from(payload, 39.9042, 116.4074, 7))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("malformed");
                });
    }
}
