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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FrankfurterClientTest {

    private static final String BASE_URL = "https://api.frankfurter.dev";

    private static final String SERIES_BODY = """
            [
              {"date": "2026-01-02", "base": "CNY", "quote": "USD", "rate": 7.31},
              {"date": "2026-01-05", "base": "CNY", "quote": "USD", "rate": 7.29},
              {"date": "2026-01-06", "base": "CNY", "quote": "USD", "rate": 7.33}
            ]
            """;

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private FrankfurterClient client() {
        TravelProviderProperties properties = new TravelProviderProperties(
                "https://api.open-meteo.com", BASE_URL, "https://v6.exchangerate-api.com",
                Duration.ofSeconds(3), Duration.ofSeconds(8));
        return new FrankfurterClient(builder, properties, new ObjectMapper());
    }

    @Test
    void fetchSeries_sendsOneGetWithBaseQuotesFromAndTo() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/rates")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("base", "CNY"))
                .andExpect(queryParam("quotes", "USD"))
                .andExpect(queryParam("from", "2026-01-01"))
                .andExpect(queryParam("to", "2026-03-31"))
                .andRespond(withSuccess(SERIES_BODY, MediaType.APPLICATION_JSON));

        List<FrankfurterClient.SeriesPoint> series =
                client().fetchSeries("CNY", "USD", "2026-01-01", "2026-03-31");

        assertThat(series).extracting(FrankfurterClient.SeriesPoint::date)
                .containsExactly("2026-01-02", "2026-01-05", "2026-01-06");
        assertThat(series).extracting(FrankfurterClient.SeriesPoint::rate)
                .containsExactly(7.31, 7.29, 7.33);
        server.verify();
    }

    @Test
    void fetchSeries_providerDaysOutOfOrder_seriesIsAscendingWithGapsPreserved() {
        // The provider days skip the 2026-01-03/04 weekend: exactly those days are returned —
        // nothing interpolated, nothing padded — and shuffled provider order is re-sorted.
        server.expect(requestTo(startsWith(BASE_URL + "/v2/rates")))
                .andRespond(withSuccess("""
                        [
                          {"date": "2026-01-06", "base": "CNY", "quote": "USD", "rate": 7.33},
                          {"date": "2026-01-02", "base": "CNY", "quote": "USD", "rate": 7.31},
                          {"date": "2026-01-05", "base": "CNY", "quote": "USD", "rate": 7.29}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<FrankfurterClient.SeriesPoint> series =
                client().fetchSeries("CNY", "USD", "2026-01-01", "2026-01-07");

        assertThat(series).extracting(FrankfurterClient.SeriesPoint::date)
                .containsExactly("2026-01-02", "2026-01-05", "2026-01-06");
        server.verify();
    }

    @Test
    void fetchSeries_invalidCurrency_throwsInputRejectionCarryingTheProviderMessage() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/rates")))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"status\": 422, \"message\": \"invalid currency: ABC\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().fetchSeries("CNY", "ABC", "2026-01-01", "2026-01-07"))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.INPUT_REJECTION);
                    assertThat(e.reason()).contains("invalid currency: ABC");
                });
        server.verify();
    }

    @Test
    void fetchSeries_providerServerError_throwsAvailability() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/rates")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("boom")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client().fetchSeries("CNY", "USD", "2026-01-01", "2026-01-07"))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("500");
                });
        server.verify();
    }

    @Test
    void fetchSeries_entryMissingRate_throwsAvailability() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/rates")))
                .andRespond(withSuccess("[{\"date\": \"2026-01-02\"}]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().fetchSeries("CNY", "USD", "2026-01-01", "2026-01-07"))
                .isInstanceOfSatisfying(TravelProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.reason()).contains("malformed");
                });
        server.verify();
    }
}
