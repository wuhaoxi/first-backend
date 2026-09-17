package com.first.app.config;

import com.first.app.controller.AttractionCommentController;
import com.first.app.controller.AttractionController;
import com.first.app.controller.AttractionFavoriteController;
import com.first.app.dto.AttractionResponse;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtService;
import com.first.app.service.AttractionCommentService;
import com.first.app.service.AttractionFavoriteService;
import com.first.app.service.AttractionInteractionEnricher;
import com.first.app.service.AttractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 */
@WebMvcTest({
        AttractionFavoriteController.class,
        AttractionCommentController.class,
        AttractionController.class
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
}
