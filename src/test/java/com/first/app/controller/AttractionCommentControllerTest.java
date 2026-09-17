package com.first.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.AttractionCommentResponse;
import com.first.app.dto.CreateCommentRequest;
import com.first.app.dto.PageResponse;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.repository.UserRepository;
import com.first.app.service.AttractionCommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttractionCommentController.class)
@AutoConfigureMockMvc(addFilters = false)
class AttractionCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AttractionCommentService attractionCommentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    private AttractionCommentResponse buildResponse(Long id, Long attractionId, Long userId,
                                                    Long parentId, String content) {
        return AttractionCommentResponse.builder()
                .id(id).attractionId(attractionId).userId(userId)
                .parentCommentId(parentId).content(content)
                .build();
    }

    private String jsonBody(String content) throws Exception {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void create_returns201() throws Exception {
        when(attractionCommentService.createTopLevel(eq(1L), any(CreateCommentRequest.class), eq(1L)))
                .thenReturn(buildResponse(10L, 1L, 1L, null, "Beautiful palace"));

        mockMvc.perform(post("/api/attractions/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("Beautiful palace"))
                        .requestAttr("userId", 1L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.attractionId").value(1))
                .andExpect(jsonPath("$.content").value("Beautiful palace"))
                .andExpect(jsonPath("$.parentCommentId").doesNotExist());
    }

    @Test
    void create_unauthorized_returns401() throws Exception {
        mockMvc.perform(post("/api/attractions/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("Beautiful palace")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_blankContent_returns400() throws Exception {
        mockMvc.perform(post("/api/attractions/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(""))
                        .requestAttr("userId", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void reply_returns201() throws Exception {
        when(attractionCommentService.reply(eq(5L), any(CreateCommentRequest.class), eq(1L)))
                .thenReturn(buildResponse(11L, 1L, 1L, 5L, "agreed"));

        mockMvc.perform(post("/api/attraction-comments/5/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("agreed"))
                        .requestAttr("userId", 1L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentCommentId").value(5))
                .andExpect(jsonPath("$.attractionId").value(1));
    }

    @Test
    void reply_unauthorized_returns401() throws Exception {
        mockMvc.perform(post("/api/attraction-comments/5/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("agreed")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findTopLevel_public_returns200() throws Exception {
        PageResponse<AttractionCommentResponse> page = new PageResponse<>(
                List.of(buildResponse(1L, 1L, 1L, null, "c1")), 0, 20, 1, 1);
        when(attractionCommentService.findTopLevel(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/attractions/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void findTopLevel_sizeAboveMax_isClampedTo100() throws Exception {
        when(attractionCommentService.findTopLevel(eq(1L), any())).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(1);
            return new PageResponse<AttractionCommentResponse>(
                    List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        });

        mockMvc.perform(get("/api/attractions/1/comments").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void findReplies_public_returns200() throws Exception {
        PageResponse<AttractionCommentResponse> page = new PageResponse<>(
                List.of(buildResponse(2L, 1L, 2L, 5L, "r1")), 0, 20, 1, 1);
        when(attractionCommentService.findReplies(eq(5L), any())).thenReturn(page);

        mockMvc.perform(get("/api/attraction-comments/5/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].parentCommentId").value(5));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/attraction-comments/10")
                        .requestAttr("userId", 1L))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_unauthorized_returns401() throws Exception {
        mockMvc.perform(delete("/api/attraction-comments/10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_forbidden_returns403() throws Exception {
        doThrow(new AccessDeniedException("You can only delete your own comments"))
                .when(attractionCommentService).delete(eq(10L), eq(2L));

        mockMvc.perform(delete("/api/attraction-comments/10")
                        .requestAttr("userId", 2L))
                .andExpect(status().isForbidden());
    }
}
