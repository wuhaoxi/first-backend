package com.first.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.first.app.dto.ExchangeRateHistoryResponse;
import com.first.app.dto.ExchangeRateLatestResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.UpstreamUnavailableException;
import com.first.app.integration.ExchangeRateApiClient;
import com.first.app.integration.FrankfurterClient;
import com.first.app.integration.TravelProviderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final String AS_OF_DATE = "2026-10-05";

    private static final Map<String, Double> RATES = Map.of(
            "CNY", 1.0,
            "USD", 7.12,
            "EUR", 7.85,
            "JPY", 0.048,
            "KRW", 0.0053,
            "GBP", 9.1);

    private static final List<FrankfurterClient.SeriesPoint> SERIES = List.of(
            new FrankfurterClient.SeriesPoint("2026-01-02", 7.28),
            new FrankfurterClient.SeriesPoint("2026-01-05", 7.31));

    @Mock
    private ExchangeRateApiClient exchangeRateApiClient;

    @Mock
    private FrankfurterClient frankfurterClient;

    @Mock
    private RankingCacheService cache;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    private static void assertErrorsOn(Throwable thrown, String... fields) {
        assertThat(thrown).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) thrown).getErrors())
                .extracting(error -> error.get("field"))
                .containsExactly(fields);
    }

    // ---------- latest form ----------

    @Test
    void latest_defaultsToCnyAndCuratedQuotes() {
        cacheMissRunsLoader();
        stubLatestProviderEcho();

        ExchangeRateLatestResponse response = exchangeRateService.getLatest(null, null);

        assertThat(response.base()).isEqualTo("CNY");
        assertThat(response.quotes()).containsExactly(
                new ExchangeRateLatestResponse.Quote("USD", 7.12),
                new ExchangeRateLatestResponse.Quote("EUR", 7.85),
                new ExchangeRateLatestResponse.Quote("JPY", 0.048),
                new ExchangeRateLatestResponse.Quote("KRW", 0.0053));
        assertThat(response.asOfDate()).isEqualTo(AS_OF_DATE);
        assertThat(response.attribution()).isEqualTo("Rates By Exchange Rate API");

        verify(exchangeRateApiClient).fetchLatest("CNY", List.of("USD", "EUR", "JPY", "KRW"));
        verifyNoInteractions(frankfurterClient);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(cache).getOrLoad(keyCaptor.capture(), ttlCaptor.capture(),
                ArgumentMatchers.<TypeReference<ExchangeRateLatestResponse>>any(),
                ArgumentMatchers.<Supplier<ExchangeRateLatestResponse>>any());
        assertThat(keyCaptor.getValue()).isEqualTo("travel:rates:latest:CNY:EUR,JPY,KRW,USD");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void latest_blankInputsFallBackToDefaults() {
        cacheMissRunsLoader();
        stubLatestProviderEcho();

        ExchangeRateLatestResponse response = exchangeRateService.getLatest("", ",");

        assertThat(response.base()).isEqualTo("CNY");
        assertThat(response.quotes()).hasSize(4);
        verify(exchangeRateApiClient).fetchLatest("CNY", List.of("USD", "EUR", "JPY", "KRW"));
    }

    @Test
    void latest_normalizesCasingAndWhitespacePreservingRequestOrder() {
        cacheMissRunsLoader();
        stubLatestProviderEcho();

        ExchangeRateLatestResponse response = exchangeRateService.getLatest(" cny ", " usd , eur ");

        assertThat(response.base()).isEqualTo("CNY");
        assertThat(response.quotes()).containsExactly(
                new ExchangeRateLatestResponse.Quote("USD", 7.12),
                new ExchangeRateLatestResponse.Quote("EUR", 7.85));
        verify(exchangeRateApiClient).fetchLatest("CNY", List.of("USD", "EUR"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cache).getOrLoad(keyCaptor.capture(), any(Duration.class),
                ArgumentMatchers.<TypeReference<ExchangeRateLatestResponse>>any(),
                ArgumentMatchers.<Supplier<ExchangeRateLatestResponse>>any());
        assertThat(keyCaptor.getValue()).isEqualTo("travel:rates:latest:CNY:EUR,USD");
    }

    @Test
    void latest_deduplicatesPreservingFirstOccurrenceOrder() {
        cacheMissRunsLoader();
        stubLatestProviderEcho();

        ExchangeRateLatestResponse response = exchangeRateService.getLatest("CNY", "USD,USD,EUR");

        assertThat(response.quotes()).containsExactly(
                new ExchangeRateLatestResponse.Quote("USD", 7.12),
                new ExchangeRateLatestResponse.Quote("EUR", 7.85));
        verify(exchangeRateApiClient).fetchLatest("CNY", List.of("USD", "EUR"));
    }

    @Test
    void latest_reorderedQuoteListsShareOneCacheEntry() {
        cacheMissRunsLoader();
        stubLatestProviderEcho();

        exchangeRateService.getLatest("CNY", "EUR,USD");
        exchangeRateService.getLatest("CNY", "USD,EUR");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cache, times(2)).getOrLoad(keyCaptor.capture(), any(Duration.class),
                ArgumentMatchers.<TypeReference<ExchangeRateLatestResponse>>any(),
                ArgumentMatchers.<Supplier<ExchangeRateLatestResponse>>any());
        assertThat(keyCaptor.getAllValues()).containsExactly(
                "travel:rates:latest:CNY:EUR,USD", "travel:rates:latest:CNY:EUR,USD");
    }

    @Test
    void latest_malformedCodesAreRejectedWithoutAnyCall() {
        assertThatThrownBy(() -> exchangeRateService.getLatest("CN1", "USD"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("base")
                .hasMessageContaining("CN1")
                .satisfies(ex -> assertErrorsOn(ex, "base"));
        assertThatThrownBy(() -> exchangeRateService.getLatest("CNY", "US"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("quotes")
                .hasMessageContaining("US")
                .satisfies(ex -> assertErrorsOn(ex, "quotes"));

        verifyNoInteractions(exchangeRateApiClient, frankfurterClient, cache);
    }

    @Test
    void latest_reversedPairIsServedAsRequested() {
        cacheMissRunsLoader();
        stubLatestProviderEcho();

        ExchangeRateLatestResponse response = exchangeRateService.getLatest(" usd ", "cny");

        assertThat(response.base()).isEqualTo("USD");
        assertThat(response.quotes())
                .containsExactly(new ExchangeRateLatestResponse.Quote("CNY", 1.0));
        verify(exchangeRateApiClient).fetchLatest("USD", List.of("CNY"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cache).getOrLoad(keyCaptor.capture(), any(Duration.class),
                ArgumentMatchers.<TypeReference<ExchangeRateLatestResponse>>any(),
                ArgumentMatchers.<Supplier<ExchangeRateLatestResponse>>any());
        assertThat(keyCaptor.getValue()).isEqualTo("travel:rates:latest:USD:CNY");
    }

    @Test
    void latest_cacheHitSkipsProviders() {
        ExchangeRateLatestResponse cached = new ExchangeRateLatestResponse("CNY", AS_OF_DATE,
                List.of(new ExchangeRateLatestResponse.Quote("USD", 7.12)),
                ExchangeRateLatestResponse.ATTRIBUTION);
        when(cache.getOrLoad(anyString(), any(Duration.class),
                ArgumentMatchers.<TypeReference<ExchangeRateLatestResponse>>any(),
                ArgumentMatchers.<Supplier<ExchangeRateLatestResponse>>any())).thenReturn(cached);

        ExchangeRateLatestResponse response = exchangeRateService.getLatest("CNY", "USD");

        assertThat(response).isEqualTo(cached);
        verifyNoInteractions(exchangeRateApiClient, frankfurterClient);
    }

    @Test
    void latest_cacheHitStillOrdersQuotesAsRequested() {
        ExchangeRateLatestResponse cached = new ExchangeRateLatestResponse("CNY", AS_OF_DATE,
                List.of(
                        new ExchangeRateLatestResponse.Quote("USD", 7.12),
                        new ExchangeRateLatestResponse.Quote("EUR", 7.85)),
                ExchangeRateLatestResponse.ATTRIBUTION);
        when(cache.getOrLoad(anyString(), any(Duration.class),
                ArgumentMatchers.<TypeReference<ExchangeRateLatestResponse>>any(),
                ArgumentMatchers.<Supplier<ExchangeRateLatestResponse>>any())).thenReturn(cached);

        ExchangeRateLatestResponse response = exchangeRateService.getLatest("CNY", "EUR,USD");

        assertThat(response.quotes()).containsExactly(
                new ExchangeRateLatestResponse.Quote("EUR", 7.85),
                new ExchangeRateLatestResponse.Quote("USD", 7.12));
        verifyNoInteractions(exchangeRateApiClient, frankfurterClient);
    }

    @Test
    void latest_clientInputRejectionBecomesBadRequest() {
        cacheMissRunsLoader();
        when(exchangeRateApiClient.fetchLatest(anyString(), ArgumentMatchers.<List<String>>any()))
                .thenThrow(new TravelProviderException(TravelProviderException.Kind.INPUT_REJECTION,
                        "ExchangeRate-API does not support currency code ABC"));

        assertThatThrownBy(() -> exchangeRateService.getLatest("CNY", "ABC"))
                .isInstanceOf(InvalidRequestException.class)
                .isNotInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("ABC");
    }

    @Test
    void latest_availabilityFailureBecomesUpstreamUnavailable() {
        cacheMissRunsLoader();
        when(exchangeRateApiClient.fetchLatest(anyString(), ArgumentMatchers.<List<String>>any()))
                .thenThrow(new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                        "EXCHANGE_RATE_API_KEY is not set: the latest-rate form requires an ExchangeRate-API key"));

        assertThatThrownBy(() -> exchangeRateService.getLatest("CNY", "USD"))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("EXCHANGE_RATE_API_KEY");
    }

    // ---------- series form ----------

    @Test
    void history_requiresQuoteFromAndTo() {
        assertThatThrownBy(() -> exchangeRateService.getHistory("CNY", null, "2026-01-01", "2026-03-31"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("quote")
                .satisfies(ex -> assertErrorsOn(ex, "quote"));
        assertThatThrownBy(() -> exchangeRateService.getHistory("CNY", "USD", null, "2026-03-31"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from")
                .satisfies(ex -> assertErrorsOn(ex, "from"));
        assertThatThrownBy(() -> exchangeRateService.getHistory("CNY", "USD", "2026-01-01", null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("to")
                .satisfies(ex -> assertErrorsOn(ex, "to"));

        verifyNoInteractions(exchangeRateApiClient, frankfurterClient, cache);
    }

    @Test
    void history_rejectsMalformedDate() {
        assertThatThrownBy(
                () -> exchangeRateService.getHistory("CNY", "USD", "2026/01/01", "2026-03-31"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from")
                .hasMessageContaining("2026/01/01")
                .satisfies(ex -> assertErrorsOn(ex, "from"));
        assertThatThrownBy(
                () -> exchangeRateService.getHistory("CNY", "USD", "2026-01-01", "not-a-date"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("to")
                .hasMessageContaining("not-a-date")
                .satisfies(ex -> assertErrorsOn(ex, "to"));

        verifyNoInteractions(exchangeRateApiClient, frankfurterClient, cache);
    }

    @Test
    void history_rejectsInvertedRange() {
        assertThatThrownBy(
                () -> exchangeRateService.getHistory("CNY", "USD", "2026-03-31", "2026-01-01"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("after")
                .satisfies(ex -> assertErrorsOn(ex, "from"));

        verifyNoInteractions(exchangeRateApiClient, frankfurterClient, cache);
    }

    @Test
    void history_rejectsFutureDates() {
        String from = LocalDate.now().minusDays(5).toString();
        String to = LocalDate.now().plusDays(1).toString();
        assertThatThrownBy(() -> exchangeRateService.getHistory("CNY", "USD", from, to))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("future")
                .satisfies(ex -> assertErrorsOn(ex, "from", "to"));

        String tomorrow = LocalDate.now().plusDays(1).toString();
        String dayAfter = LocalDate.now().plusDays(2).toString();
        assertThatThrownBy(() -> exchangeRateService.getHistory("CNY", "USD", tomorrow, dayAfter))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("future")
                .satisfies(ex -> assertErrorsOn(ex, "from", "to"));

        verifyNoInteractions(exchangeRateApiClient, frankfurterClient, cache);
    }

    @Test
    void history_rejectsSpansOver366Days() {
        assertThatThrownBy(
                () -> exchangeRateService.getHistory("CNY", "USD", "2025-01-01", "2026-01-02"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("366")
                .satisfies(ex -> assertErrorsOn(ex, "from", "to"));

        verifyNoInteractions(exchangeRateApiClient, frankfurterClient, cache);
    }

    @Test
    void history_acceptsExactly366DaySpan() {
        cacheMissRunsLoader();
        stubSeriesProvider();

        ExchangeRateHistoryResponse response =
                exchangeRateService.getHistory("CNY", "USD", "2025-01-01", "2026-01-01");

        assertThat(response.requestedDays()).isEqualTo(366);
        assertThat(response.returnedDays()).isEqualTo(SERIES.size());
    }

    @Test
    void history_normalizesAndUsesSeriesKeyAndTtl() {
        cacheMissRunsLoader();
        stubSeriesProvider();

        ExchangeRateHistoryResponse response =
                exchangeRateService.getHistory(" cny ", "usd", "2026-01-01", "2026-03-31");

        assertThat(response.base()).isEqualTo("CNY");
        assertThat(response.quote()).isEqualTo("USD");
        assertThat(response.fromDate()).isEqualTo("2026-01-01");
        assertThat(response.toDate()).isEqualTo("2026-03-31");
        assertThat(response.series()).containsExactly(
                new ExchangeRateHistoryResponse.SeriesPoint("2026-01-02", 7.28),
                new ExchangeRateHistoryResponse.SeriesPoint("2026-01-05", 7.31));
        assertThat(response.requestedDays()).isEqualTo(90);
        assertThat(response.returnedDays()).isEqualTo(2);
        assertThat(response.attribution()).isEqualTo("Daily reference rates via Frankfurter");

        verify(frankfurterClient).fetchSeries("CNY", "USD", "2026-01-01", "2026-03-31");
        verifyNoInteractions(exchangeRateApiClient);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(cache).getOrLoad(keyCaptor.capture(), ttlCaptor.capture(),
                ArgumentMatchers.<TypeReference<ExchangeRateHistoryResponse>>any(),
                ArgumentMatchers.<Supplier<ExchangeRateHistoryResponse>>any());
        assertThat(keyCaptor.getValue())
                .isEqualTo("travel:rates:series:CNY:USD:2026-01-01:2026-03-31");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void history_cacheHitSkipsProviders() {
        ExchangeRateHistoryResponse cached = new ExchangeRateHistoryResponse("CNY", "USD",
                "2026-01-01", "2026-03-31",
                List.of(new ExchangeRateHistoryResponse.SeriesPoint("2026-01-02", 7.28)), 90, 1,
                ExchangeRateHistoryResponse.ATTRIBUTION);
        when(cache.getOrLoad(anyString(), any(Duration.class),
                ArgumentMatchers.<TypeReference<ExchangeRateHistoryResponse>>any(),
                ArgumentMatchers.<Supplier<ExchangeRateHistoryResponse>>any())).thenReturn(cached);

        ExchangeRateHistoryResponse response =
                exchangeRateService.getHistory("CNY", "USD", "2026-01-01", "2026-03-31");

        assertThat(response).isEqualTo(cached);
        verifyNoInteractions(exchangeRateApiClient, frankfurterClient);
    }

    @Test
    void history_clientInputRejectionBecomesBadRequest() {
        cacheMissRunsLoader();
        when(frankfurterClient.fetchSeries(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new TravelProviderException(TravelProviderException.Kind.INPUT_REJECTION,
                        "Frankfurter responded with HTTP 422: invalid currency: ABC"));

        assertThatThrownBy(
                () -> exchangeRateService.getHistory("CNY", "ABC", "2026-01-01", "2026-03-31"))
                .isInstanceOf(InvalidRequestException.class)
                .isNotInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("ABC");
    }

    @Test
    void history_availabilityFailureBecomesUpstreamUnavailable() {
        cacheMissRunsLoader();
        when(frankfurterClient.fetchSeries(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                        "Frankfurter responded with HTTP 500"));

        assertThatThrownBy(
                () -> exchangeRateService.getHistory("CNY", "USD", "2026-01-01", "2026-03-31"))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("500");
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private <T> void cacheMissRunsLoader() {
        when(cache.getOrLoad(anyString(), any(Duration.class),
                ArgumentMatchers.<TypeReference<T>>any(), ArgumentMatchers.<Supplier<T>>any()))
                .thenAnswer(invocation -> ((Supplier<T>) invocation.getArgument(3)).get());
    }

    private void stubLatestProviderEcho() {
        when(exchangeRateApiClient.fetchLatest(anyString(), ArgumentMatchers.<List<String>>any()))
                .thenAnswer(invocation -> {
                    String base = invocation.getArgument(0);
                    List<String> quotes = invocation.getArgument(1);
                    return new ExchangeRateApiClient.LatestRates(base, AS_OF_DATE, quotes.stream()
                            .map(currency -> new ExchangeRateApiClient.Quote(currency,
                                    RATES.get(currency)))
                            .toList());
                });
    }

    private void stubSeriesProvider() {
        when(frankfurterClient.fetchSeries(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(SERIES);
    }
}
