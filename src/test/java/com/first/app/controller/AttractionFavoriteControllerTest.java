package com.first.app.controller;

import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.AttractionFavoriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttractionFavoriteController.class)
@AutoConfigureMockMvc(addFilters = false)
class AttractionFavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttractionFavoriteService attractionFavoriteService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void toggle_returns200WithFavoritedTrue() throws Exception {
        when(attractionFavoriteService.toggle(1L, 1L)).thenReturn(true);

        mockMvc.perform(post("/api/attractions/1/favorite")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));
    }

    @Test
    void toggle_returns200WithFavoritedFalse() throws Exception {
        when(attractionFavoriteService.toggle(1L, 1L)).thenReturn(false);

        mockMvc.perform(post("/api/attractions/1/favorite")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));
    }

    @Test
    void toggle_unauthorized_returns401() throws Exception {
        mockMvc.perform(post("/api/attractions/1/favorite"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void toggle_unknownAttraction_returns404ErrorBody() throws Exception {
        when(attractionFavoriteService.toggle(99L, 1L))
                .thenThrow(new ResourceNotFoundException("Attraction not found with id: 99"));

        mockMvc.perform(post("/api/attractions/99/favorite")
                        .requestAttr("userId", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Attraction not found with id: 99"));
    }
}
