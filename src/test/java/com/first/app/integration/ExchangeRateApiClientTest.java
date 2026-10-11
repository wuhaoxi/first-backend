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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExchangeRateApiClientTest {

    private static final String BASE_URL = "https://v6.exchangerate-api.com";
    private static final String DUMMY_KEY = "test-key";

    // conversion_rates is deliberately shuffled relative to the request: the client must return
    // quotes in request order, not provider order.
    private static final String LATEST_BODY = """
            {
              "result": "success",
              "time_last_update_utc": "Mon, 05 Oct 2026 00:00:01 +0000",
              "base_code": "CNY",
              "conversion_rates": {"KRW": 191.52, "JPY": 20.41, "EUR": 0.13245, "USD": 0.14924}
            }
            """;

    private MockRestServiceServer server;

    // Keep a single builder bound to the mock server for every client instance in a test.
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private ExchangeRateApiClient clientWithKey(String key) {
        TravelProviderProperties properties = new TravelProviderProperties(
                "https://api.open-meteo.com", "https://api.frankfurter.dev", BASE_URL,
                Duration.ofSeconds(3), Duration.ofSeconds(8));
        return new ExchangeRateApiClient(builder, properties, new ObjectMapper(), key);
    }

    @Test
    void fetchLatest_sendsOneKeyedGetWithNoQueryParameters_andQuotesFollowRequestOrder() {
        server.expect(requestTo(BASE_URL + "/v6/" + DUMMY_KEY + "/latest/CNY"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(LATEST_BODY, MediaType.APPLICATION_JSON));

        ExchangeRateApiClient.LatestRates latest =
                clientWithKey(DUMMY_KEY).fetchLatest("CNY", List.of("USD", "EUR", "JPY", "KRW"));

        assertThat(latest.base()).isEqualTo("CNY");
        assertThat(latest.asOfDate()).isEqualTo("2026-10-05");
        assertThat(latest.quotes()).extracting(ExchangeRateApiClient.Quote::currency)
                .containsExactly("USD", "EUR", "JPY", "KRW");
        assertThat(latest.quotes()).extracting(ExchangeRateApiClient.Quote::rate)
                .containsExactly(0.14924, 0.13245, 20.41, 191.52);
        server.verify();
    }

    @Test
    void fetchLatest_missingKey_throwsAvailabilityNamingTheVariable_withoutAnyHttpCall() {
        // No expectation is registered: any HTTP attempt would fail this test as an
        // unexpected request, so the missing-key path is pinned to issue no provider call.
        assertThatThrownBy(() -> clientWithKey(null).fetchLatest("CNY", List.of("USD")))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("EXCHANGE_RATE_API_KEY");
                });
        server.verify();
    }

    @Test
    void fetchLatest_unsupportedCode_throwsInputRejectionNamingTheBaseCode() {
        server.expect(requestTo(BASE_URL + "/v6/" + DUMMY_KEY + "/latest/ABC"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"result\": \"error\", \"error-type\": \"unsupported-code\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clientWithKey(DUMMY_KEY).fetchLatest("ABC", List.of("USD")))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.INPUT_REJECTION);
                    assertThat(e.reason()).contains("ABC");
                    assertThat(e.reason()).contains("unsupported-code");
                });
        server.verify();
    }

    @Test
    void fetchLatest_planUpgradeRequired_throwsAvailability() {
        server.expect(requestTo(BASE_URL + "/v6/" + DUMMY_KEY + "/latest/CNY"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"result\": \"error\", \"error-type\": \"plan-upgrade-required\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clientWithKey(DUMMY_KEY).fetchLatest("CNY", List.of("USD")))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("plan-upgrade-required");
                });
        server.verify();
    }

    @Test
    void fetchLatest_nonSuccessResultOnHttp200_isStillAvailability() {
        server.expect(requestTo(BASE_URL + "/v6/" + DUMMY_KEY + "/latest/CNY"))
                .andRespond(withSuccess("{\"result\": \"error\", \"error-type\": \"quota-reached\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clientWithKey(DUMMY_KEY).fetchLatest("CNY", List.of("USD")))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("quota-reached");
                });
        server.verify();
    }

    @Test
    void fetchLatest_quoteMissingFromConversionRates_throwsInputRejectionNamingTheCurrency() {
        server.expect(requestTo(BASE_URL + "/v6/" + DUMMY_KEY + "/latest/CNY"))
                .andRespond(withSuccess("""
                        {
                          "result": "success",
                          "time_last_update_utc": "Mon, 05 Oct 2026 00:00:01 +0000",
                          "base_code": "CNY",
                          "conversion_rates": {"USD": 0.14924}
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clientWithKey(DUMMY_KEY).fetchLatest("CNY", List.of("ABC")))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.INPUT_REJECTION);
                    assertThat(e.reason()).contains("ABC");
                });
        server.verify();
    }

    @Test
    void fetchLatest_successBodyMissingFields_throwsAvailability() {
        server.expect(requestTo(BASE_URL + "/v6/" + DUMMY_KEY + "/latest/CNY"))
                .andRespond(withSuccess("{\"result\": \"success\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clientWithKey(DUMMY_KEY).fetchLatest("CNY", List.of("USD")))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("missing expected fields");
                });
        server.verify();
    }
}
