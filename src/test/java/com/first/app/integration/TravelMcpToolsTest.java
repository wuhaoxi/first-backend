package com.first.app.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelMcpToolsTest {

    @Mock
    private TravelWeatherService weatherService;

    @Mock
    private ExchangeRateService exchangeRateService;

    private ObjectMapper objectMapper;
    private TravelMcpTools travelMcpTools;

    @BeforeEach
    void setUp() {
        objectMapper = Jackson2ObjectMapperBuilder.json().build();
        travelMcpTools = new TravelMcpTools(weatherService, exchangeRateService, objectMapper);
    }

    // ---------- get_weather ----------

    @Test
    void getWeather_fullWindow_returnsFrozenPayloadShape() throws Exception {
        when(weatherService.getWeather(39.9042, 116.4074, 7, 7)).thenReturn(weatherPayload());

        McpSchema.CallToolResult result = travelMcpTools.getWeather(Map.of(
                "latitude", 39.9042, "longitude", 116.4074,
                "forecastDays", 7, "pastDays", 7));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        JsonNode payload = payloadOf(result);
        assertThat(fieldNames(payload)).containsExactly(
                "attribution", "current", "daily", "observedAt", "timezone");
        assertThat(payload.path("timezone").asText()).isEqualTo("Asia/Shanghai");
        assertThat(payload.path("observedAt").asText()).isEqualTo("2026-10-05T10:00");
        assertThat(payload.path("attribution").asText())
                .isEqualTo("Weather data by Open-Meteo (CC BY 4.0)");

        JsonNode current = payload.path("current");
        assertThat(fieldNames(current)).containsExactly("apparentTemperatureC", "humidityPercent",
                "precipitationMm", "temperatureC", "weatherCode", "windDirectionDegrees",
                "windSpeedKmh");
        assertThat(current.path("temperatureC").asDouble()).isEqualTo(21.4);
        assertThat(current.path("weatherCode").asInt()).isEqualTo(2);

        JsonNode daily = payload.path("daily");
        assertThat(daily).hasSize(3);
        assertThat(fieldNames(daily.get(0))).containsExactly("date",
                "precipitationProbabilityPercent", "precipitationSumMm", "temperatureMaxC",
                "temperatureMinC", "weatherCode", "windSpeedMaxKmh");
        assertThat(daily.get(0).path("date").asText()).isEqualTo("2026-10-04");
        assertThat(daily.get(1).path("date").asText()).isEqualTo("2026-10-05");
        assertThat(daily.get(2).path("date").asText()).isEqualTo("2026-10-06");

        verify(weatherService).getWeather(39.9042, 116.4074, 7, 7);
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void getWeather_optionalArgsDefaulted() throws Exception {
        when(weatherService.getWeather(39.9042, 116.4074, 7, 0)).thenReturn(weatherPayload());

        McpSchema.CallToolResult result =
                travelMcpTools.getWeather(Map.of("latitude", 39.9042, "longitude", 116.4074));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        verify(weatherService).getWeather(39.9042, 116.4074, 7, 0);
    }

    @Test
    void getWeather_outOfRangeCoordinates_rejectedLocally() throws Exception {
        assertToolError(
                travelMcpTools.getWeather(Map.of("latitude", 95, "longitude", 116.4074,
                        "forecastDays", 7, "pastDays", 7)),
                "input-rejection", "latitude must be between -90 and 90 (got 95)");
        assertToolError(
                travelMcpTools.getWeather(Map.of("latitude", 39.9, "longitude", -200)),
                "input-rejection", "longitude must be between -180 and 180 (got -200)");

        verifyNoInteractions(weatherService, exchangeRateService);
    }

    @Test
    void getWeather_missingLatitude_isRequired() throws Exception {
        assertToolError(travelMcpTools.getWeather(Map.of("longitude", 116.4074)),
                "input-rejection", "latitude is required");

        verifyNoInteractions(weatherService, exchangeRateService);
    }

    @Test
    void getWeather_invalidForecastDays_rejectedLocally() throws Exception {
        assertToolError(
                travelMcpTools.getWeather(Map.of("latitude", 39.9, "longitude", 116.4,
                        "forecastDays", 2.5)),
                "input-rejection", "forecastDays must be an integer (got 2.5)");
        assertToolError(
                travelMcpTools.getWeather(Map.of("latitude", 39.9, "longitude", 116.4,
                        "forecastDays", 9)),
                "input-rejection", "forecastDays must be between 1 and 7 (got 9)");

        verifyNoInteractions(weatherService, exchangeRateService);
    }

    @Test
    void getWeather_invalidRequestException_mapsToInputRejection() throws Exception {
        when(weatherService.getWeather(39.9, 116.4, 7, 0))
                .thenThrow(new InvalidRequestException("latitude must be between -90 and 90 (got 95)"));

        assertToolError(travelMcpTools.getWeather(Map.of("latitude", 39.9, "longitude", 116.4)),
                "input-rejection", "latitude must be between -90 and 90 (got 95)");
    }

    @Test
    void getWeather_upstreamUnavailable_mapsToUpstreamUnavailable() throws Exception {
        when(weatherService.getWeather(39.9, 116.4, 7, 0))
                .thenThrow(new UpstreamUnavailableException("Open-Meteo request failed with status 500"));

        assertToolError(travelMcpTools.getWeather(Map.of("latitude", 39.9, "longitude", 116.4)),
                "upstream-unavailable", "Open-Meteo request failed with status 500");
    }

    @Test
    void getWeather_unexpectedFailure_stillReturnsToolError() throws Exception {
        when(weatherService.getWeather(39.9, 116.4, 7, 0))
                .thenThrow(new IllegalStateException("boom"));

        assertToolError(travelMcpTools.getWeather(Map.of("latitude", 39.9, "longitude", 116.4)),
                "upstream-unavailable", "boom");
    }

    // ---------- get_exchange_rate ----------

    @Test
    void getExchangeRate_latestWithoutDates_returnsFrozenPayloadShape() throws Exception {
        when(exchangeRateService.getLatest("CNY", "USD")).thenReturn(latestFixture());

        McpSchema.CallToolResult result =
                travelMcpTools.getExchangeRate(Map.of("base", "CNY", "quote", "USD"));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        JsonNode payload = payloadOf(result);
        assertThat(fieldNames(payload)).containsExactly("asOfDate", "attribution", "base", "quotes");
        assertThat(payload.path("base").asText()).isEqualTo("CNY");
        assertThat(payload.path("asOfDate").asText()).isEqualTo("2026-10-05");
        assertThat(payload.path("attribution").asText()).isEqualTo("Rates By Exchange Rate API");

        JsonNode quotes = payload.path("quotes");
        assertThat(quotes).hasSize(1);
        assertThat(fieldNames(quotes.get(0))).containsExactly("currency", "rate");
        assertThat(quotes.get(0).path("currency").asText()).isEqualTo("USD");
        assertThat(quotes.get(0).path("rate").asDouble()).isEqualTo(7.12);

        verify(exchangeRateService).getLatest("CNY", "USD");
        verifyNoInteractions(weatherService);
    }

    @Test
    void getExchangeRate_multiQuoteLatest_keepsRequestOrder() throws Exception {
        when(exchangeRateService.getLatest("CNY", "USD,EUR,JPY,KRW")).thenReturn(
                new ExchangeRateLatestResponse("CNY", "2026-10-05",
                        List.of(
                                new ExchangeRateLatestResponse.Quote("USD", 7.12),
                                new ExchangeRateLatestResponse.Quote("EUR", 7.85),
                                new ExchangeRateLatestResponse.Quote("JPY", 0.048),
                                new ExchangeRateLatestResponse.Quote("KRW", 0.0053)),
                        ExchangeRateLatestResponse.ATTRIBUTION));

        McpSchema.CallToolResult result = travelMcpTools.getExchangeRate(
                Map.of("base", "CNY", "quote", "USD,EUR,JPY,KRW"));

        JsonNode quotes = payloadOf(result).path("quotes");
        assertThat(quotes).hasSize(4);
        assertThat(quotes.get(0).path("currency").asText()).isEqualTo("USD");
        assertThat(quotes.get(1).path("currency").asText()).isEqualTo("EUR");
        assertThat(quotes.get(2).path("currency").asText()).isEqualTo("JPY");
        assertThat(quotes.get(3).path("currency").asText()).isEqualTo("KRW");

        verify(exchangeRateService).getLatest("CNY", "USD,EUR,JPY,KRW");
    }

    @Test
    void getExchangeRate_malformedCodes_rejectedLocally() throws Exception {
        assertToolError(travelMcpTools.getExchangeRate(Map.of("base", "cnh ", "quote", "USD")),
                "input-rejection", "base must be a 3-letter ISO-4217 code (got \"cnh \")");
        assertToolError(travelMcpTools.getExchangeRate(Map.of("base", "CNY", "quote", "US")),
                "input-rejection",
                "quote must be a 3-letter ISO-4217 code or a comma-separated list of them (got \"US\")");

        verifyNoInteractions(weatherService, exchangeRateService);
    }

    @Test
    void getExchangeRate_datesMustBeProvidedTogether() throws Exception {
        assertToolError(
                travelMcpTools.getExchangeRate(Map.of("base", "CNY", "quote", "USD",
                        "fromDate", "2026-01-01")),
                "input-rejection", "fromDate and toDate must be provided together");

        verifyNoInteractions(weatherService, exchangeRateService);
    }

    @Test
    void getExchangeRate_seriesRequiresSingleQuoteCode() throws Exception {
        assertToolError(
                travelMcpTools.getExchangeRate(Map.of("base", "CNY", "quote", "USD,EUR",
                        "fromDate", "2026-01-01", "toDate", "2026-03-31")),
                "input-rejection",
                "quote must be a single 3-letter ISO-4217 code when fromDate/toDate are provided (got \"USD,EUR\")");

        verifyNoInteractions(weatherService, exchangeRateService);
    }

    @Test
    void getExchangeRate_seriesRejectsMalformedDates() throws Exception {
        assertToolError(
                travelMcpTools.getExchangeRate(Map.of("base", "CNY", "quote", "USD",
                        "fromDate", "2026/01/01", "toDate", "2026-03-31")),
                "input-rejection",
                "fromDate must be an ISO date (YYYY-MM-DD) (got \"2026/01/01\")");

        verifyNoInteractions(weatherService, exchangeRateService);
    }

    @Test
    void getExchangeRate_series_returnsFrozenShapeWithoutRestOnlyFields() throws Exception {
        when(exchangeRateService.getHistory("CNY", "USD", "2026-01-01", "2026-03-31"))
                .thenReturn(historyFixture());

        McpSchema.CallToolResult result = travelMcpTools.getExchangeRate(Map.of(
                "base", "CNY", "quote", "USD",
                "fromDate", "2026-01-01", "toDate", "2026-03-31"));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        JsonNode payload = payloadOf(result);
        assertThat(fieldNames(payload)).containsExactly(
                "attribution", "base", "fromDate", "series", "toDate");
        // REST-only fields must not leak into the frozen MCP series shape.
        assertThat(payload.has("quote")).isFalse();
        assertThat(payload.has("requestedDays")).isFalse();
        assertThat(payload.has("returnedDays")).isFalse();

        JsonNode series = payload.path("series");
        assertThat(series).hasSize(2);
        assertThat(fieldNames(series.get(0))).containsExactly("date", "rate");
        assertThat(series.get(0).path("date").asText()).isEqualTo("2026-01-02");
        assertThat(series.get(1).path("date").asText()).isEqualTo("2026-01-05");
        assertThat(payload.path("attribution").asText())
                .isEqualTo("Daily reference rates via Frankfurter");

        verify(exchangeRateService).getHistory("CNY", "USD", "2026-01-01", "2026-03-31");
        verifyNoInteractions(weatherService);
    }

    @Test
    void getExchangeRate_inputRejection_mapsToInputRejection() throws Exception {
        when(exchangeRateService.getLatest("CNY", "ABC")).thenThrow(new InvalidRequestException(
                "ExchangeRate-API does not support currency code ABC"));

        assertToolError(travelMcpTools.getExchangeRate(Map.of("base", "CNY", "quote", "ABC")),
                "input-rejection", "ExchangeRate-API does not support currency code ABC");
    }

    @Test
    void getExchangeRate_upstreamUnavailable_mapsToUpstreamUnavailable() throws Exception {
        when(exchangeRateService.getLatest("CNY", "USD")).thenThrow(new UpstreamUnavailableException(
                "EXCHANGE_RATE_API_KEY is not set: the latest-rate form requires an ExchangeRate-API key"));

        assertToolError(travelMcpTools.getExchangeRate(Map.of("base", "CNY", "quote", "USD")),
                "upstream-unavailable",
                "EXCHANGE_RATE_API_KEY is not set: the latest-rate form requires an ExchangeRate-API key");
    }

    // ---------- helpers ----------

    private JsonNode payloadOf(McpSchema.CallToolResult result) throws Exception {
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        return objectMapper.readTree(text.text());
    }

    private void assertToolError(McpSchema.CallToolResult result, String kind, String reason)
            throws Exception {
        assertThat(result.isError()).isTrue();
        JsonNode payload = payloadOf(result);
        assertThat(fieldNames(payload)).containsExactly("error");
        JsonNode error = payload.path("error");
        assertThat(fieldNames(error)).containsExactly("kind", "reason");
        assertThat(error.path("kind").asText()).isEqualTo(kind);
        assertThat(error.path("reason").asText()).isEqualTo(reason);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static WeatherForecastResponse weatherPayload() {
        return new WeatherForecastResponse(null, null, null, 39.9042, 116.4074, 7,
                "Asia/Shanghai", "2026-10-05T10:00",
                new WeatherResponse.Current(21.4, 20.1, 65.0, 0.0, 8.5, 180.0, 2),
                List.of(
                        new WeatherDailyResponse("2026-10-04", 22.0, 12.0, 0.0, 10.0, 14.0, 1),
                        new WeatherDailyResponse("2026-10-05", 21.5, 11.4, 0.6, 55.0, 16.5, 61),
                        new WeatherDailyResponse("2026-10-06", 20.8, 10.9, 1.2, 70.0, 12.0, 63)),
                WeatherResponse.ATTRIBUTION);
    }

    private static ExchangeRateLatestResponse latestFixture() {
        return new ExchangeRateLatestResponse("CNY", "2026-10-05",
                List.of(new ExchangeRateLatestResponse.Quote("USD", 7.12)),
                ExchangeRateLatestResponse.ATTRIBUTION);
    }

    private static ExchangeRateHistoryResponse historyFixture() {
        return new ExchangeRateHistoryResponse("CNY", "USD", "2026-01-01", "2026-03-31",
                List.of(
                        new ExchangeRateHistoryResponse.SeriesPoint("2026-01-02", 7.28),
                        new ExchangeRateHistoryResponse.SeriesPoint("2026-01-05", 7.31)),
                90, 2, ExchangeRateHistoryResponse.ATTRIBUTION);
    }
}
