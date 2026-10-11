package com.first.app.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenMeteoClientTest {

    private static final String BASE_URL = "https://api.open-meteo.com";

    private static final String CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,"
                    + "wind_speed_10m,wind_direction_10m,weather_code";

    private static final String DAILY_FIELDS =
            "temperature_2m_max,temperature_2m_min,precipitation_sum,"
                    + "precipitation_probability_max,wind_speed_10m_max,weather_code";

    private static final String SUCCESS_BODY = """
            {
              "latitude": 39.9,
              "longitude": 116.4,
              "generationtime_ms": 0.42,
              "utc_offset_seconds": 28800,
              "timezone": "Asia/Shanghai",
              "current": {
                "time": "2026-10-05T10:00",
                "temperature_2m": 31.5,
                "apparent_temperature": 34.2,
                "relative_humidity_2m": 58,
                "precipitation": 0.0,
                "wind_speed_10m": 12.3,
                "wind_direction_10m": 180,
                "weather_code": 1
              },
              "daily": {
                "time": ["2026-10-05", "2026-10-06"],
                "temperature_2m_max": [33.1, 32.4],
                "temperature_2m_min": [21.0, 20.5],
                "precipitation_sum": [0.0, 1.2],
                "precipitation_probability_max": [10, 35],
                "wind_speed_10m_max": [18.4, 15.2],
                "weather_code": [1, 3]
              }
            }
            """;

    private MockRestServiceServer server;
    private OpenMeteoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TravelProviderProperties properties = new TravelProviderProperties(
                BASE_URL, "https://api.frankfurter.dev", "https://v6.exchangerate-api.com",
                Duration.ofSeconds(3), Duration.ofSeconds(8));
        client = new OpenMeteoClient(builder, properties, new ObjectMapper());
    }

    @Test
    void fetchWeather_sendsSingleForecastGetWithEveryParameter() {
        server.expect(requestTo(startsWith(BASE_URL + "/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", "39.9042"))
                .andExpect(queryParam("longitude", "116.4074"))
                .andExpect(queryParam("current", CURRENT_FIELDS))
                .andExpect(queryParam("daily", DAILY_FIELDS))
                .andExpect(queryParam("forecast_days", "7"))
                .andExpect(queryParam("past_days", "0"))
                .andExpect(queryParam("timezone", "auto"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        OpenMeteoClient.WeatherPayload payload = client.fetchWeather(39.9042, 116.4074, 7, 0);

        assertThat(payload.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(payload.current().time()).isEqualTo("2026-10-05T10:00");
        assertThat(payload.current().temperature2m()).isEqualTo(31.5);
        assertThat(payload.current().weatherCode()).isEqualTo(1);
        assertThat(payload.daily().time()).containsExactly("2026-10-05", "2026-10-06");
        assertThat(payload.daily().temperature2mMax()).containsExactly(33.1, 32.4);
        assertThat(payload.daily().weatherCode()).containsExactly(1, 3);
        server.verify();
    }

    @Test
    void fetchWeather_historyWindow_usesPastDaysOnForecastApiNeverArchive() {
        // MockRestServiceServer rejects any request without a matching expectation, so a call to
        // /v1/archive could never pass silently — this test pins the forecast path for past windows.
        server.expect(requestTo(startsWith(BASE_URL + "/v1/forecast")))
                .andExpect(queryParam("forecast_days", "1"))
                .andExpect(queryParam("past_days", "7"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        OpenMeteoClient.WeatherPayload payload = client.fetchWeather(30.5728, 104.0668, 1, 7);

        assertThat(payload.daily().time()).hasSize(2);
        server.verify();
    }

    @Test
    void fetchWeather_providerErrorWithReason_throwsAvailabilityCarryingProviderReason() {
        server.expect(requestTo(startsWith(BASE_URL + "/v1/forecast")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": true, \"reason\": \"Latitude must be in range of -90 to 90\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchWeather(95, 116.4, 7, 0))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).isEqualTo("Latitude must be in range of -90 to 90");
                });
        server.verify();
    }

    @Test
    void fetchWeather_malformedSuccessBody_throwsAvailability() {
        server.expect(requestTo(startsWith(BASE_URL + "/v1/forecast")))
                .andRespond(withSuccess("{\"timezone\": \"GMT\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchWeather(39.9042, 116.4074, 7, 0))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("malformed");
                });
        server.verify();
    }

    @Test
    void fetchWeather_nonJsonSuccessBody_throwsAvailability() {
        server.expect(requestTo(startsWith(BASE_URL + "/v1/forecast")))
                .andRespond(withSuccess("<html>gateway hiccup</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.fetchWeather(39.9042, 116.4074, 7, 0))
                .isInstanceOfSatisfying(TravelProviderException.class, e ->
                        assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE));
        server.verify();
    }
}
