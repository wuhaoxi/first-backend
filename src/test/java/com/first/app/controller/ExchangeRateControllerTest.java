package com.first.app.controller;

import com.first.app.dto.ExchangeRateHistoryResponse;
import com.first.app.dto.ExchangeRateLatestResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.UpstreamUnavailableException;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the REST contract of {@link ExchangeRateController} with a mocked
 * {@link ExchangeRateService}: parameter forwarding incl. defaults, field-level {@code errors[]}
 * for input rejections (400 — never 502 for an upstream-rejected code), and 502 for availability
 * failures.
 */
@WebMvcTest(ExchangeRateController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExchangeRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExchangeRateService exchangeRateService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    // ---- fixtures --------------------------------------------------------------------------

    private static ExchangeRateLatestResponse latestResponse() {
        return new ExchangeRateLatestResponse("CNY", "2026-10-05",
                List.of(
                        new ExchangeRateLatestResponse.Quote("USD", 0.149),
                        new ExchangeRateLatestResponse.Quote("EUR", 0.128)),
                ExchangeRateLatestResponse.ATTRIBUTION);
    }

    private static ExchangeRateHistoryResponse historyResponse() {
        return new ExchangeRateHistoryResponse("CNY", "USD", "2026-01-01", "2026-03-31",
                List.of(
                        new ExchangeRateHistoryResponse.SeriesPoint("2026-01-01", 0.14309),
                        new ExchangeRateHistoryResponse.SeriesPoint("2026-01-02", 0.14314)),
                90, 2, ExchangeRateHistoryResponse.ATTRIBUTION);
    }

    // ---- GET /api/exchange-rates/latest ----------------------------------------------------

    @Test
    void latest_returns200WithDefaults() throws Exception {
        when(exchangeRateService.getLatest(null, null)).thenReturn(latestResponse());

        mockMvc.perform(get("/api/exchange-rates/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("CNY"))
                .andExpect(jsonPath("$.asOfDate").value("2026-10-05"))
                .andExpect(jsonPath("$.quotes.length()").value(2))
                .andExpect(jsonPath("$.quotes[0].currency").value("USD"))
                .andExpect(jsonPath("$.quotes[0].rate").value(0.149))
                .andExpect(jsonPath("$.quotes[1].currency").value("EUR"))
                .andExpect(jsonPath("$.attribution").value("Rates By Exchange Rate API"));

        verify(exchangeRateService).getLatest(null, null);
    }

    @Test
    void latest_forwardsExplicitParameters() throws Exception {
        ExchangeRateLatestResponse usdBase = new ExchangeRateLatestResponse("USD", "2026-10-05",
                List.of(new ExchangeRateLatestResponse.Quote("CNY", 6.71)),
                ExchangeRateLatestResponse.ATTRIBUTION);
        when(exchangeRateService.getLatest("USD", "CNY")).thenReturn(usdBase);

        mockMvc.perform(get("/api/exchange-rates/latest?base=USD&quotes=CNY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("USD"))
                .andExpect(jsonPath("$.quotes[0].currency").value("CNY"));

        verify(exchangeRateService).getLatest("USD", "CNY");
    }

    @Test
    void latest_malformedCode_returns400WithFieldErrors() throws Exception {
        when(exchangeRateService.getLatest("CNY", "US")).thenThrow(new InvalidRequestException(
                "quotes must be a 3-letter ISO-4217 code or a comma-separated list of them (got \"US\")",
                "quotes"));
        when(exchangeRateService.getLatest("CN1", "USD")).thenThrow(new InvalidRequestException(
                "base must be a 3-letter ISO-4217 code (got \"CN1\")", "base"));

        mockMvc.perform(get("/api/exchange-rates/latest?base=CNY&quotes=US"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("quotes"));

        mockMvc.perform(get("/api/exchange-rates/latest?base=CN1&quotes=USD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("base"));
    }

    @Test
    void latest_upstreamRejectsCode_returns400Not502() throws Exception {
        when(exchangeRateService.getLatest("CNY", "ABC")).thenThrow(new InvalidRequestException(
                "ExchangeRate-API does not support currency code ABC"));

        mockMvc.perform(get("/api/exchange-rates/latest?base=CNY&quotes=ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("ExchangeRate-API does not support currency code ABC"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void latest_availabilityFailure_returns502() throws Exception {
        when(exchangeRateService.getLatest(null, null)).thenThrow(new UpstreamUnavailableException(
                "EXCHANGE_RATE_API_KEY is not set: the latest-rate form requires an ExchangeRate-API key"));

        mockMvc.perform(get("/api/exchange-rates/latest"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "EXCHANGE_RATE_API_KEY is not set: the latest-rate form requires an ExchangeRate-API key"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---- GET /api/exchange-rates/history ---------------------------------------------------

    @Test
    void history_returns200WithSeriesAndCounters() throws Exception {
        when(exchangeRateService.getHistory("CNY", "USD", "2026-01-01", "2026-03-31"))
                .thenReturn(historyResponse());

        mockMvc.perform(get(
                "/api/exchange-rates/history?base=CNY&quote=USD&from=2026-01-01&to=2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("CNY"))
                .andExpect(jsonPath("$.quote").value("USD"))
                .andExpect(jsonPath("$.fromDate").value("2026-01-01"))
                .andExpect(jsonPath("$.toDate").value("2026-03-31"))
                .andExpect(jsonPath("$.series.length()").value(2))
                .andExpect(jsonPath("$.series[0].date").value("2026-01-01"))
                .andExpect(jsonPath("$.series[1].date").value("2026-01-02"))
                .andExpect(jsonPath("$.requestedDays").value(90))
                .andExpect(jsonPath("$.returnedDays").value(2))
                .andExpect(jsonPath("$.attribution").value("Daily reference rates via Frankfurter"));

        verify(exchangeRateService).getHistory("CNY", "USD", "2026-01-01", "2026-03-31");
    }

    @Test
    void history_missingTo_returns400NamingTo() throws Exception {
        when(exchangeRateService.getHistory("CNY", "USD", "2026-01-01", null))
                .thenThrow(new InvalidRequestException("to is required", "to"));

        mockMvc.perform(get("/api/exchange-rates/history?base=CNY&quote=USD&from=2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("to is required"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("to"));
    }

    @Test
    void history_invertedRange_returns400NamingFrom() throws Exception {
        when(exchangeRateService.getHistory("CNY", "USD", "2026-03-31", "2026-01-01"))
                .thenThrow(new InvalidRequestException(
                        "from must not be after to (got 2026-03-31 > 2026-01-01)", "from"));

        mockMvc.perform(get(
                "/api/exchange-rates/history?base=CNY&quote=USD&from=2026-03-31&to=2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    void history_availabilityFailure_returns502() throws Exception {
        when(exchangeRateService.getHistory("CNY", "USD", "2026-01-01", "2026-03-31"))
                .thenThrow(new UpstreamUnavailableException("Frankfurter responded with HTTP 500"));

        mockMvc.perform(get(
                "/api/exchange-rates/history?base=CNY&quote=USD&from=2026-01-01&to=2026-03-31"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Frankfurter responded with HTTP 500"))
                .andExpect(jsonPath("$.status").value(502));
    }
}
