package com.first.app.controller;

import com.first.app.dto.ReindexResponse;
import com.first.app.exception.KnowledgeBaseUnavailableException;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.KnowledgeIndexService;
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

@WebMvcTest(KnowledgeBaseController.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeBaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeIndexService knowledgeIndexService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void reindex_returnsCountsSummary() throws Exception {
        when(knowledgeIndexService.reindexAll()).thenReturn(new ReindexResponse(3, 5, 2, 11));

        mockMvc.perform(post("/api/knowledge-base/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts").value(3))
                .andExpect(jsonPath("$.attractions").value(5))
                .andExpect(jsonPath("$.cities").value(2))
                .andExpect(jsonPath("$.chunks").value(11));
    }

    @Test
    void reindex_storeUnavailable_returns503ErrorResponse() throws Exception {
        when(knowledgeIndexService.reindexAll())
                .thenThrow(new KnowledgeBaseUnavailableException("Knowledge base store unavailable"));

        mockMvc.perform(post("/api/knowledge-base/reindex"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Knowledge base store unavailable"))
                .andExpect(jsonPath("$.status").value(503));
    }
}
