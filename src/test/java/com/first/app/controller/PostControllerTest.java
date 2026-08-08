package com.first.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.CreatePostRequest;
import com.first.app.dto.ImageUploadResponse;
import com.first.app.dto.PostResponse;
import com.first.app.dto.PostSummary;
import com.first.app.dto.UpdatePostRequest;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.repository.UserRepository;
import com.first.app.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostService postService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    private Post buildPost(Long id, PostStatus status, Long authorId) {
        return Post.builder()
                .id(id).title("Test Post").content("# Hello")
                .status(status).authorId(authorId)
                .tags(List.of("travel"))
                .commentCount(0)
                .createdAt(LocalDateTime.of(2026, 8, 8, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 8, 8, 10, 0))
                .build();
    }

    @Test
    void create_returns201() throws Exception {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("Test Post");
        req.setContent("# Hello");

        Post post = buildPost(1L, PostStatus.DRAFT, 1L);

        when(postService.create(any(CreatePostRequest.class), anyLong())).thenReturn(post);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("userId", 1L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Post"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.authorId").value(1));
    }

    @Test
    void create_unauthorized_returns401() throws Exception {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("Test");
        req.setContent("# H");

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findAll_returns200() throws Exception {
        Post post1 = buildPost(1L, PostStatus.PUBLISHED, 1L);
        Post post2 = buildPost(2L, PostStatus.PUBLISHED, 2L);

        when(postService.findPublishedList()).thenReturn(List.of(post2, post1));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Test Post"))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    void findById_published_returns200() throws Exception {
        Post post = buildPost(1L, PostStatus.PUBLISHED, 1L);

        when(postService.findByIdPublic(1L)).thenReturn(post);

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Post"))
                .andExpect(jsonPath("$.content").value("# Hello"))
                .andExpect(jsonPath("$.authorId").value(1));
    }

    @Test
    void findById_draft_returns404() throws Exception {
        when(postService.findByIdPublic(99L))
                .thenThrow(new ResourceNotFoundException("Post not found with id: 99"));

        mockMvc.perform(get("/api/posts/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returns200() throws Exception {
        UpdatePostRequest req = new UpdatePostRequest();
        req.setTitle("Updated");

        Post post = buildPost(1L, PostStatus.DRAFT, 1L);
        post.setTitle("Updated");

        when(postService.update(eq(1L), any(UpdatePostRequest.class), eq(1L))).thenReturn(post);

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));
    }

    @Test
    void update_unauthorized_returns401() throws Exception {
        UpdatePostRequest req = new UpdatePostRequest();
        req.setTitle("Updated");

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_forbidden_returns400() throws Exception {
        UpdatePostRequest req = new UpdatePostRequest();
        req.setTitle("Updated");

        when(postService.update(eq(1L), any(UpdatePostRequest.class), eq(2L)))
                .thenThrow(new InvalidRequestException("You can only edit your own posts"));

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("userId", 2L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/posts/1")
                        .requestAttr("userId", 1L))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_unauthorized_returns401() throws Exception {
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadImage_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", "fake-image".getBytes());

        ImageUploadResponse response = new ImageUploadResponse("/api/uploads/posts/1/cover.jpg");

        when(postService.uploadImage(eq(1L), any(), eq(1L))).thenReturn(response);

        mockMvc.perform(multipart("/api/posts/1/image")
                        .file(file)
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/api/uploads/posts/1/cover.jpg"));
    }

    @Test
    void uploadImage_unauthorized_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", "fake-image".getBytes());

        mockMvc.perform(multipart("/api/posts/1/image").file(file))
                .andExpect(status().isUnauthorized());
    }
}
