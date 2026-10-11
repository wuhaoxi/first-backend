package com.first.app.controller;

import com.first.app.dto.WeatherDailyResponse;
import com.first.app.dto.WeatherForecastResponse;
import com.first.app.dto.WeatherHistoryResponse;
import com.first.app.dto.WeatherResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.exception.UpstreamUnavailableException;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.TravelWeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the REST contract of {@link WeatherController} with a mocked {@link TravelWeatherService}:
 * selector and {@code days} validation surface as 400 with field-level {@code errors[]}, unknown
 * slugs as 404, provider failures as 502 — and valid windows delegate to the shared service.
 */
@WebMvcTest(WeatherController.class)
@AutoConfigureMockMvc(addFilters = false)
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TravelWeatherService travelWeatherService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    // ---- fixtures --------------------------------------------------------------------------

    private static WeatherResponse.Current currentConditions() {
        return new WeatherResponse.Current(17.4, 14.7, 33.0, 0.0, 5.2, 248.0, 0);
    }

    private static WeatherResponse currentResponse() {
        return new WeatherResponse("beijing", null, "Beijing", 39.9042, 116.4074, "Asia/Shanghai",
                "2026-10-05T22:45", currentConditions(), WeatherResponse.ATTRIBUTION);
    }

    private static WeatherForecastResponse forecastResponse(int days) {
        return new WeatherForecastResponse("beijing", null, "Beijing", 39.9042, 116.4074, days,
                "Asia/Shanghai", "2026-10-05T22:45", currentConditions(),
                List.of(
                        new WeatherDailyResponse("2026-10-05", 24.9, 11.4, 0.0, 0.0, 13.8, 1),
                        new WeatherDailyResponse("2026-10-06", 25.4, 13.3, 0.0, 0.0, 12.2, 0)),
                WeatherResponse.ATTRIBUTION);
    }

    private static WeatherHistoryResponse historyResponse() {
        return new WeatherHistoryResponse(null, "west-lake", "West Lake", 30.25, 120.15, 7,
                "Asia/Shanghai",
                List.of(
                        new WeatherHistoryResponse.Day("2026-09-29", 27.5, 18.1, 0.0, 12.3),
                        new WeatherHistoryResponse.Day("2026-09-30", 26.8, 17.9, 0.4, 10.5)),
                WeatherResponse.ATTRIBUTION);
    }

    // ---- GET /api/weather/current ----------------------------------------------------------

    @Test
    void current_returns200WithWeatherShape() throws Exception {
        when(travelWeatherService.getCurrent("beijing", null)).thenReturn(currentResponse());

        mockMvc.perform(get("/api/weather/current?citySlug=beijing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citySlug").value("beijing"))
                .andExpect(jsonPath("$.locationName").value("Beijing"))
                .andExpect(jsonPath("$.latitude").value(39.9042))
                .andExpect(jsonPath("$.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.observedAt").value("2026-10-05T22:45"))
                .andExpect(jsonPath("$.current.temperatureC").value(17.4))
                .andExpect(jsonPath("$.current.weatherCode").value(0))
                .andExpect(jsonPath("$.attribution").value(WeatherResponse.ATTRIBUTION));
    }

    @Test
    void current_attractionSelector_isForwarded() throws Exception {
        WeatherResponse attractionCurrent = new WeatherResponse(null, "forbidden-city",
                "Forbidden City", 39.9163, 116.3972, "Asia/Shanghai", "2026-10-05T22:45",
                currentConditions(), WeatherResponse.ATTRIBUTION);
        when(travelWeatherService.getCurrent(null, "forbidden-city")).thenReturn(attractionCurrent);

        mockMvc.perform(get("/api/weather/current?attractionSlug=forbidden-city"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attractionSlug").value("forbidden-city"))
                .andExpect(jsonPath("$.locationName").value("Forbidden City"));

        verify(travelWeatherService).getCurrent(null, "forbidden-city");
    }

    @Test
    void current_neitherSelector_returns400WithFieldErrors() throws Exception {
        when(travelWeatherService.getCurrent(null, null)).thenThrow(new InvalidRequestException(
                "exactly one of citySlug or attractionSlug must be supplied", "citySlug",
                "attractionSlug"));

        mockMvc.perform(get("/api/weather/current"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("exactly one of citySlug or attractionSlug must be supplied"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("citySlug"))
                .andExpect(jsonPath("$.errors[1].field").value("attractionSlug"));
    }

    @Test
    void current_bothSelectors_returns400WithFieldErrors() throws Exception {
        when(travelWeatherService.getCurrent("beijing", "forbidden-city"))
                .thenThrow(new InvalidRequestException(
                        "exactly one of citySlug or attractionSlug must be supplied", "citySlug",
                        "attractionSlug"));

        mockMvc.perform(get("/api/weather/current?citySlug=beijing&attractionSlug=forbidden-city"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("citySlug"))
                .andExpect(jsonPath("$.errors[1].field").value("attractionSlug"));
    }

    @Test
    void current_unknownSlug_returns404ErrorBody() throws Exception {
        when(travelWeatherService.getCurrent("atlantis", null))
                .thenThrow(new ResourceNotFoundException("city not found: atlantis"));

        mockMvc.perform(get("/api/weather/current?citySlug=atlantis"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("city not found: atlantis"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void current_upstreamUnavailable_returns502() throws Exception {
        when(travelWeatherService.getCurrent("beijing", null))
                .thenThrow(new UpstreamUnavailableException("Open-Meteo refused the connection"));

        mockMvc.perform(get("/api/weather/current?citySlug=beijing"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Open-Meteo refused the connection"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---- GET /api/weather/forecast ---------------------------------------------------------

    @Test
    void forecast_returns200WithDailyEntries() throws Exception {
        when(travelWeatherService.getForecast("beijing", null, 5)).thenReturn(forecastResponse(5));

        mockMvc.perform(get("/api/weather/forecast?citySlug=beijing&days=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.daily.length()").value(2))
                .andExpect(jsonPath("$.daily[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$.daily[1].date").value("2026-10-06"))
                .andExpect(jsonPath("$.locationName").value("Beijing"))
                .andExpect(jsonPath("$.attribution").value(WeatherResponse.ATTRIBUTION));

        verify(travelWeatherService).getForecast("beijing", null, 5);
    }

    @Test
    void forecast_daysOmitted_delegatesToTheServiceDefault() throws Exception {
        when(travelWeatherService.getForecast("beijing", null, null))
                .thenReturn(forecastResponse(7));

        mockMvc.perform(get("/api/weather/forecast?citySlug=beijing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7));

        verify(travelWeatherService).getForecast("beijing", null, null);
    }

    @Test
    void forecast_outOfRangeDays_returns400NamingDays() throws Exception {
        mockMvc.perform(get("/api/weather/forecast?citySlug=beijing&days=14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("days"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("days must be between 3 and 7 (got 14)"));

        mockMvc.perform(get("/api/weather/forecast?citySlug=beijing&days=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("days"));

        verifyNoInteractions(travelWeatherService);
    }

    @Test
    void forecast_nonNumericDays_returns400NamingDays() throws Exception {
        mockMvc.perform(get("/api/weather/forecast?citySlug=beijing&days=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("days"))
                .andExpect(jsonPath("$.message").value("days must be an integer (got \"abc\")"));

        verifyNoInteractions(travelWeatherService);
    }

    // ---- GET /api/weather/history ----------------------------------------------------------

    @Test
    void history_returns200WithObservationEntries() throws Exception {
        when(travelWeatherService.getHistory(null, "west-lake", null)).thenReturn(historyResponse());

        mockMvc.perform(get("/api/weather/history?attractionSlug=west-lake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attractionSlug").value("west-lake"))
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.daily.length()").value(2))
                .andExpect(jsonPath("$.daily[0].date").value("2026-09-29"))
                .andExpect(jsonPath("$.daily[1].date").value("2026-09-30"))
                .andExpect(jsonPath("$.attribution").value(WeatherResponse.ATTRIBUTION));

        verify(travelWeatherService).getHistory(null, "west-lake", null);
    }

    @Test
    void history_outOfRangeDays_returns400NamingDays() throws Exception {
        mockMvc.perform(get("/api/weather/history?attractionSlug=west-lake&days=90"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("days"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("days must be between 7 and 30 (got 90)"));

        mockMvc.perform(get("/api/weather/history?attractionSlug=west-lake&days=3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("days"));

        verifyNoInteractions(travelWeatherService);
    }
}
