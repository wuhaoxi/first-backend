package com.first.app.service;

import com.first.app.dto.CreatePostRequest;
import com.first.app.dto.UpdatePostRequest;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.dto.ImageUploadResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private Post buildPost(Long id, PostStatus status, Long authorId) {
        return Post.builder()
                .id(id).title("Test Post").content("# Hello")
                .status(status).authorId(authorId)
                .tags(List.of("travel"))
                .build();
    }

    @Test
    void shouldCreatePostWithDefaultDraftStatus() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("# Hello");

        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        Post result = postService.create(request, AUTHOR_ID);

        assertThat(result.getTitle()).isEqualTo("Test Post");
        assertThat(result.getContent()).isEqualTo("# Hello");
        assertThat(result.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(result.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(result.getCommentCount()).isEqualTo(0);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void shouldCreatePostWithExplicitPublishedStatus() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Published");
        request.setContent("# Pub");
        request.setStatus(PostStatus.PUBLISHED);

        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });

        Post result = postService.create(request, AUTHOR_ID);

        assertThat(result.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void shouldFindPublishedList() {
        Post post1 = buildPost(1L, PostStatus.PUBLISHED, AUTHOR_ID);
        Post post2 = buildPost(2L, PostStatus.PUBLISHED, OTHER_USER_ID);

        when(postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED))
                .thenReturn(List.of(post2, post1));

        List<Post> result = postService.findPublishedList();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Test Post");
    }

    @Test
    void shouldFindById() {
        Post post = buildPost(1L, PostStatus.PUBLISHED, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        Post result = postService.findById(1L);

        assertThat(result.getTitle()).isEqualTo("Test Post");
    }

    @Test
    void shouldThrow404WhenPostNotFound() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found with id: 999");
    }

    @Test
    void findByIdPublic_shouldReturnPublishedPost() {
        Post post = buildPost(1L, PostStatus.PUBLISHED, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        Post result = postService.findByIdPublic(1L);

        assertThat(result.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void findByIdPublic_shouldHideDraft() {
        Post post = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.findByIdPublic(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldAllowAuthorToUpdatePost() {
        Post post = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("New Title");

        Post result = postService.update(1L, request, AUTHOR_ID);

        assertThat(result.getTitle()).isEqualTo("New Title");
    }

    @Test
    void shouldThrow400WhenNonAuthorUpdates() {
        Post post = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("New Title");

        assertThatThrownBy(() -> postService.update(1L, request, OTHER_USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("You can only edit your own posts");
    }

    @Test
    void shouldDeletePost() {
        Post post = buildPost(1L, PostStatus.PUBLISHED, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        postService.delete(1L, AUTHOR_ID);

        verify(postRepository).delete(post);
    }

    @Test
    void shouldThrow400WhenNonAuthorDeletes() {
        Post post = buildPost(1L, PostStatus.PUBLISHED, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(1L, OTHER_USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void shouldAllowStatusChangeInUpdate() {
        Post post = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        UpdatePostRequest request = new UpdatePostRequest();
        request.setStatus(PostStatus.PUBLISHED);

        Post result = postService.update(1L, request, AUTHOR_ID);

        assertThat(result.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void shouldCreatePostWithCoverImage() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("With Cover");
        request.setContent("# Cover");
        request.setCoverImage("https://example.com/photo.jpg");

        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(12L);
            return p;
        });

        Post result = postService.create(request, AUTHOR_ID);

        assertThat(result.getCoverImage()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void shouldSanitizeAndValidateTags() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Tagged");
        request.setContent("# Tags");
        request.setTags(List.of("  tag1  ", "", "tag2"));

        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post result = postService.create(request, AUTHOR_ID);

        assertThat(result.getTags()).containsExactly("tag1", "tag2");
    }

    @Test
    void shouldUploadImageAndUpdateCoverImage(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(postService, "uploadDir", tempDir.toString());

        Post post = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", "fake-image-data".getBytes());

        ImageUploadResponse response = postService.uploadImage(1L, file, AUTHOR_ID);

        assertThat(response.getUrl()).isEqualTo("/api/uploads/posts/1/cover.jpg");
        assertThat(post.getCoverImage()).isEqualTo("/api/uploads/posts/1/cover.jpg");
        assertThat(tempDir.resolve("posts/1/cover.jpg")).exists();
    }

    @Test
    void shouldRejectNonImageFile() {
        Post post = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "fake-data".getBytes());

        assertThatThrownBy(() -> postService.uploadImage(1L, file, AUTHOR_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only JPEG and PNG images are allowed");
    }

    @Test
    void shouldRejectOversizedImage() {
        Post post = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        byte[] largeData = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", largeData);

        assertThatThrownBy(() -> postService.uploadImage(1L, file, AUTHOR_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Image must not exceed 5MB");
    }

    @Test
    void shouldRejectNullFile() {
        Post post = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.uploadImage(1L, null, AUTHOR_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("No image file provided");
    }
}
