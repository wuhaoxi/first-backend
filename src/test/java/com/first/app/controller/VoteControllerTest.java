package com.first.app.controller;

import com.first.app.dto.VoteStatsResponse;
import com.first.app.entity.VoteType;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.VoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class VoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoteService voteService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void vote_returns200WithStats() throws Exception {
        VoteStatsResponse stats = VoteStatsResponse.builder()
                .upCount(1).downCount(0).userVote(VoteType.UP)
                .build();
        when(voteService.vote(eq(1L), eq(VoteType.UP), eq(1L))).thenReturn(stats);

        mockMvc.perform(post("/api/posts/1/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteType\":\"UP\"}")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upCount").value(1))
                .andExpect(jsonPath("$.downCount").value(0))
                .andExpect(jsonPath("$.userVote").value("UP"));
    }

    @Test
    void vote_unauthorized_returns401() throws Exception {
        mockMvc.perform(post("/api/posts/1/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteType\":\"UP\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void vote_invalidVoteType_returns400() throws Exception {
        mockMvc.perform(post("/api/posts/1/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteType\":\"SIDEWAYS\"}")
                        .requestAttr("userId", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("voteType must be one of: UP, DOWN"));
    }

    @Test
    void voteStats_public_returns200() throws Exception {
        VoteStatsResponse stats = VoteStatsResponse.builder()
                .upCount(3).downCount(1).userVote(null)
                .build();
        when(voteService.getVoteStats(eq(1L), isNull())).thenReturn(stats);

        mockMvc.perform(get("/api/posts/1/vote-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upCount").value(3))
                .andExpect(jsonPath("$.downCount").value(1))
                .andExpect(jsonPath("$.userVote").value(nullValue()));
    }

    @Test
    void voteStats_authenticated_includesUserVote() throws Exception {
        VoteStatsResponse stats = VoteStatsResponse.builder()
                .upCount(3).downCount(1).userVote(VoteType.DOWN)
                .build();
        when(voteService.getVoteStats(eq(1L), eq(1L))).thenReturn(stats);

        mockMvc.perform(get("/api/posts/1/vote-stats")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVote").value("DOWN"));
    }
}
