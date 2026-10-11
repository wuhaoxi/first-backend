package com.first.app.config;

import com.first.app.controller.AttractionCommentController;
import com.first.app.controller.AttractionController;
import com.first.app.controller.AttractionFavoriteController;
import com.first.app.controller.ExchangeRateController;
import com.first.app.controller.WeatherController;
import com.first.app.dto.AttractionResponse;
import com.first.app.exception.UpstreamUnavailableException;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtService;
import com.first.app.service.AttractionCommentService;
import com.first.app.service.AttractionFavoriteService;
import com.first.app.service.AttractionInteractionEnricher;
import com.first.app.service.AttractionService;
import com.first.app.service.ExchangeRateService;
import com.first.app.service.TravelWeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security filter chain with filters ENABLED — the other
 * controller slices run with {@code addFilters = false} and therefore only
 * cover the controllers' defensive null-userId branches.
 *
 * <p>Contract (living specs): anonymous writes to protected endpoints are
 * rejected by Spring Security with <strong>401</strong>, not the framework's
 * default 403. Public GET endpoints stay open.
 *
 * <p>Travel surfaces: anonymous GETs on {@code /api/weather/**} and
 * {@code /api/exchange-rates/**} stay open (an upstream 502 must pass through
 * untouched, never re-labelled 401), and an anonymous {@code POST /mcp} is
 * never answered with 401.
 */
@WebMvcTest({
        AttractionFavoriteController.class,
        AttractionCommentController.class,
        AttractionController.class,
        WeatherController.class,
        ExchangeRateController.class
})
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttractionFavoriteService attractionFavoriteService;

    @MockBean
    private AttractionCommentService attractionCommentService;

    @MockBean
    private AttractionService attractionService;

    @MockBean
    private AttractionInteractionEnricher attractionInteractionEnricher;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TravelWeatherService travelWeatherService;

    @MockBean
    private ExchangeRateService exchangeRateService;

    @Test
    void anonymousFavoriteToggle_rejectedWith401ErrorBody() throws Exception {
        mockMvc.perform(post("/api/attractions/1/favorite"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void anonymousCommentCreate_rejectedWith401() throws Exception {
        mockMvc.perform(post("/api/attractions/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCommentDelete_rejectedWith401() throws Exception {
        mockMvc.perform(delete("/api/attraction-comments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPublicGet_isAllowed() throws Exception {
        when(attractionService.getBySlug("forbidden-city")).thenReturn(new AttractionResponse());

        mockMvc.perform(get("/api/attractions/forbidden-city"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousWeatherGet_isNever401() throws Exception {
        mockMvc.perform(get("/api/weather/current").param("citySlug", "beijing"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousExchangeRateGet_isNever401() throws Exception {
        mockMvc.perform(get("/api/exchange-rates/latest"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousExchangeRate502_passesThroughNot401() throws Exception {
        // Lenient: until the route is permitted (3.8) security rejects the request
        // before the stub is consumed; once permitted the stub is always used.
        lenient().when(exchangeRateService.getLatest(null, null))
                .thenThrow(new UpstreamUnavailableException("EXCHANGE_RATE_API_KEY is not set"));

        mockMvc.perform(get("/api/exchange-rates/latest"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void anonymousMcpPost_isNot401() throws Exception {
        // /mcp lives outside /api/** and is covered by anyRequest().permitAll();
        // no matcher will be added for it. This slice has no /mcp handler, so a
        // request that passes the chain ends in 404 — anything but 401.
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isNotFound());
    }
}
