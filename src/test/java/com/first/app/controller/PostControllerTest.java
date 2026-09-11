package com.first.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.CreatePostRequest;
import com.first.app.dto.ImageUploadResponse;
import com.first.app.dto.PostListResponse;
import com.first.app.dto.PostResponse;
import com.first.app.dto.PostSummary;
import com.first.app.dto.UpdatePostRequest;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.entity.Bookmark;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.repository.BookmarkRepository;
import com.first.app.repository.UserRepository;
import com.first.app.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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
    private BookmarkRepository bookmarkRepository;

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
    void create_withLongCoverImage_returns400() throws Exception {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("Test");
        req.setContent("# Hello");
        req.setCoverImage("https://example.com/" + "a".repeat(490)); // > 500 chars total

        Post post = buildPost(1L, PostStatus.DRAFT, 1L);
        when(postService.create(any(CreatePostRequest.class), anyLong())).thenReturn(post);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("userId", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void update_withLongCoverImage_returns400() throws Exception {
        UpdatePostRequest req = new UpdatePostRequest();
        req.setTitle("Updated");
        req.setCoverImage("https://example.com/" + "a".repeat(490)); // > 500 chars total

        Post post = buildPost(1L, PostStatus.DRAFT, 1L);
        when(postService.update(eq(1L), any(UpdatePostRequest.class), eq(1L))).thenReturn(post);

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("userId", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void create_whenDataIntegrityViolation_returns409WithDetail() throws Exception {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("Test");
        req.setContent("# Hello");

        when(postService.create(any(CreatePostRequest.class), anyLong()))
                .thenThrow(new DataIntegrityViolationException("stmt",
                        new SQLException("Data too long for column 'content'", "22001")));

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("userId", 1L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Data too long for column 'content'")));
    }

    @Test
    void findAll_returns200() throws Exception {
        PostSummary summary1 = PostSummary.builder()
                .id(2L).title("Test Post").authorId(2L)
                .commentCount(3).upVoteCount(5).bookmarkCount(2)
                .createdAt(LocalDateTime.of(2026, 8, 8, 10, 0))
                .build();
        PostSummary summary2 = PostSummary.builder()
                .id(1L).title("Test Post").authorId(1L)
                .commentCount(0).upVoteCount(0).bookmarkCount(0)
                .createdAt(LocalDateTime.of(2026, 8, 8, 9, 0))
                .build();
        PostListResponse response = PostListResponse.builder()
                .content(List.of(summary1, summary2))
                .page(0).size(20).totalElements(2).totalPages(1)
                .nextCursor(null).hasMore(false)
                .build();

        when(postService.findList(null, null, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Test Post"))
                .andExpect(jsonPath("$.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.content[0].upVoteCount").value(5))
                .andExpect(jsonPath("$.content[0].bookmarkCount").value(2))
                .andExpect(jsonPath("$.content[0].commentCount").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void findAll_passesParamsToService() throws Exception {
        PostListResponse response = PostListResponse.builder()
                .content(List.of())
                .page(1).size(5).totalElements(0).totalPages(0)
                .nextCursor(null).hasMore(false)
                .build();

        when(postService.findList("upvotes", 1, 5, null)).thenReturn(response);

        mockMvc.perform(get("/api/posts")
                        .param("sort", "upvotes")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));

        verify(postService).findList("upvotes", 1, 5, null);
    }

    @Test
    void findAll_passesCursorToService() throws Exception {
        PostListResponse response = PostListResponse.builder()
                .content(List.of())
                .page(0).size(20).totalElements(0).totalPages(0)
                .nextCursor(null).hasMore(false)
                .build();

        when(postService.findList(null, null, null, "opaque-cursor")).thenReturn(response);

        mockMvc.perform(get("/api/posts").param("cursor", "opaque-cursor"))
                .andExpect(status().isOk());

        verify(postService).findList(null, null, null, "opaque-cursor");
    }

    @Test
    void findAll_invalidSort_returns400() throws Exception {
        when(postService.findList(eq("trending"), any(), any(), any()))
                .thenThrow(new InvalidRequestException("Invalid sort: trending"));

        mockMvc.perform(get("/api/posts").param("sort", "trending"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort: trending"));
    }

    @Test
    void findAll_sizeZero_returns400() throws Exception {
        when(postService.findList(any(), any(), eq(0), any()))
                .thenThrow(new InvalidRequestException("Invalid size"));

        mockMvc.perform(get("/api/posts").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid size"));
    }

    @Test
    void findAll_negativePage_returns400() throws Exception {
        when(postService.findList(any(), eq(-1), any(), any()))
                .thenThrow(new InvalidRequestException("Invalid page"));

        mockMvc.perform(get("/api/posts").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid page"));
    }

    @Test
    void findAll_cursorWithNonLatestSort_returns400() throws Exception {
        when(postService.findList(eq("upvotes"), any(), any(), eq("abc")))
                .thenThrow(new InvalidRequestException("Cursor is only supported with sort=latest"));

        mockMvc.perform(get("/api/posts")
                        .param("sort", "upvotes")
                        .param("cursor", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cursor is only supported with sort=latest"));
    }

    @Test
    void findAll_malformedCursor_returns400() throws Exception {
        when(postService.findList(isNull(), any(), any(), eq("not-base64!!")))
                .thenThrow(new InvalidRequestException("Invalid cursor"));

        mockMvc.perform(get("/api/posts").param("cursor", "not-base64!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid cursor"));
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
    void findById_bookmarked_returnsTrue() throws Exception {
        Post post = buildPost(1L, PostStatus.PUBLISHED, 1L);
        when(postService.findById(1L)).thenReturn(post);
        when(postService.findByIdPublic(1L)).thenReturn(post);
        when(bookmarkRepository.findByPostIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(Bookmark.builder().id(1L).postId(1L).userId(1L).build()));

        mockMvc.perform(get("/api/posts/1")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(true));
    }

    @Test
    void findById_notBookmarked_returnsFalse() throws Exception {
        Post post = buildPost(1L, PostStatus.PUBLISHED, 1L);
        when(postService.findById(1L)).thenReturn(post);
        when(postService.findByIdPublic(1L)).thenReturn(post);
        when(bookmarkRepository.findByPostIdAndUserId(1L, 2L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/posts/1")
                        .requestAttr("userId", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(false));
    }

    @Test
    void findById_anonymous_returnsNullBookmarked() throws Exception {
        Post post = buildPost(1L, PostStatus.PUBLISHED, 1L);
        when(postService.findByIdPublic(1L)).thenReturn(post);

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"bookmarked\":null")));
    }

    @Test
    void findById_shouldReturnDraftForAuthor() throws Exception {
        Post draftPost = buildPost(1L, PostStatus.DRAFT, 1L);

        when(postService.findById(1L)).thenReturn(draftPost);

        mockMvc.perform(get("/api/posts/1")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Post"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.authorId").value(1));
    }

    @Test
    void findById_shouldReturn404ForNonAuthorDraft() throws Exception {
        Post draftPost = buildPost(1L, PostStatus.DRAFT, 1L);

        when(postService.findById(1L)).thenReturn(draftPost);
        when(postService.findByIdPublic(1L))
                .thenThrow(new ResourceNotFoundException("Post not found with id: 1"));

        mockMvc.perform(get("/api/posts/1")
                        .requestAttr("userId", 2L))
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
