package com.first.app.controller;

import com.first.app.dto.HotPostResponse;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeService homeService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void featuredGuides_returns200_emptyList() throws Exception {
        when(homeService.getFeaturedGuides()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/home/featured-guides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void popularDestinations_returns200_emptyList() throws Exception {
        when(homeService.getPopularDestinations()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/home/popular-destinations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void hotPosts_returns200_emptyList() throws Exception {
        when(homeService.getHotPosts()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/home/hot-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void hotPosts_returns200_withPosts() throws Exception {
        HotPostResponse post = new HotPostResponse(1L, "Test Title", null, 5, "2026-08-08T10:00:00");
        when(homeService.getHotPosts()).thenReturn(List.of(post));

        mockMvc.perform(get("/api/home/hot-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Test Title"))
                .andExpect(jsonPath("$[0].commentCount").value(5))
                .andExpect(jsonPath("$[0].createdAt").value("2026-08-08T10:00:00"));
    }
}
