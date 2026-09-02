package com.first.app.controller;

import com.first.app.dto.PageResponse;
import com.first.app.dto.PostSummary;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.BookmarkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookmarkController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookmarkService bookmarkService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void toggle_returns200WithBookmarkedTrue() throws Exception {
        when(bookmarkService.toggle(1L, 1L)).thenReturn(true);

        mockMvc.perform(post("/api/posts/1/bookmark")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(true));
    }

    @Test
    void toggle_unauthorized_returns401() throws Exception {
        mockMvc.perform(post("/api/posts/1/bookmark"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_returns200PageResponse() throws Exception {
        PostSummary summary = PostSummary.builder()
                .id(1L).title("Post 1").commentCount(0)
                .build();
        PageResponse<PostSummary> page = new PageResponse<>(List.of(summary), 0, 20, 1, 1);
        when(bookmarkService.listBookmarks(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/bookmarks")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Post 1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_unauthorized_returns401() throws Exception {
        mockMvc.perform(get("/api/bookmarks"))
                .andExpect(status().isUnauthorized());
    }
}
