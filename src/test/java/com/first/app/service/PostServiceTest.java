package com.first.app.service;

import com.first.app.dto.CreatePostRequest;
import com.first.app.dto.UpdatePostRequest;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import com.first.app.dto.ImageUploadResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.PostRepository;
import com.first.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

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

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostService postService;

    private User author;
    private User otherUser;

    @BeforeEach
    void setUp() {
        author = User.builder()
                .id(1L).name("Author").email("author@test.com")
                .passwordHash("hash").status(UserStatus.ACTIVE)
                .failedLoginAttempts(0).build();

        otherUser = User.builder()
                .id(2L).name("Other").email("other@test.com")
                .passwordHash("hash").status(UserStatus.ACTIVE)
                .failedLoginAttempts(0).build();
    }

    @Test
    void shouldCreatePostWithDefaultDraftStatus() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("# Hello");

        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        Post result = postService.create(request, 1L);

        assertThat(result.getTitle()).isEqualTo("Test Post");
        assertThat(result.getContent()).isEqualTo("# Hello");
        assertThat(result.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(result.getAuthor().getId()).isEqualTo(1L);
        assertThat(result.getCommentCount()).isEqualTo(0);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void shouldCreatePostWithExplicitPublishedStatus() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Published");
        request.setContent("# Pub");
        request.setStatus(PostStatus.PUBLISHED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });

        Post result = postService.create(request, 1L);

        assertThat(result.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void shouldFindPublishedList() {
        Post post1 = Post.builder().id(1L).title("P1").content("c1").status(PostStatus.PUBLISHED).author(author).build();
        Post post2 = Post.builder().id(2L).title("P2").content("c2").status(PostStatus.PUBLISHED).author(otherUser).build();

        when(postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED))
                .thenReturn(List.of(post2, post1));

        List<Post> result = postService.findPublishedList();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("P2");
        assertThat(result.get(1).getTitle()).isEqualTo("P1");
    }

    @Test
    void shouldFindById() {
        Post post = Post.builder().id(1L).title("Post").content("c").status(PostStatus.PUBLISHED).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        Post result = postService.findById(1L);

        assertThat(result.getTitle()).isEqualTo("Post");
    }

    @Test
    void shouldThrow404WhenPostNotFound() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found with id: 999");
    }

    @Test
    void shouldAllowAuthorToUpdatePost() {
        Post post = Post.builder().id(1L).title("Old").content("c").status(PostStatus.DRAFT).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("New Title");

        Post result = postService.update(1L, request, 1L);

        assertThat(result.getTitle()).isEqualTo("New Title");
    }

    @Test
    void shouldThrow403WhenNonAuthorUpdates() {
        Post post = Post.builder().id(1L).title("Old").content("c").status(PostStatus.DRAFT).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("New Title");

        assertThatThrownBy(() -> postService.update(1L, request, 2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only edit your own posts");
    }

    @Test
    void shouldArchivePost() {
        Post post = Post.builder().id(1L).title("Post").content("c").status(PostStatus.PUBLISHED).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postService.archive(1L, 1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.ARCHIVED);
        verify(postRepository).save(post);
    }

    @Test
    void shouldThrow403WhenNonAuthorArchives() {
        Post post = Post.builder().id(1L).title("Post").content("c").status(PostStatus.PUBLISHED).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.archive(1L, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldThrow400WhenArchivedToDraft() {
        Post post = Post.builder().id(1L).title("Post").content("c").status(PostStatus.ARCHIVED).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        UpdatePostRequest request = new UpdatePostRequest();
        request.setStatus(PostStatus.DRAFT);

        assertThatThrownBy(() -> postService.update(1L, request, 1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot revert ARCHIVED to DRAFT");
    }

    @Test
    void shouldThrow400WhenPublishedToDraft() {
        Post post = Post.builder().id(1L).title("Post").content("c").status(PostStatus.PUBLISHED).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        UpdatePostRequest request = new UpdatePostRequest();
        request.setStatus(PostStatus.DRAFT);

        assertThatThrownBy(() -> postService.update(1L, request, 1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot revert PUBLISHED to DRAFT");
    }

    @Test
    void shouldSanitizeAndValidateTags() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Tagged");
        request.setContent("# Tags");
        request.setTags(List.of("  tag1  ", "", "tag2"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post result = postService.create(request, 1L);

        assertThat(result.getTags()).containsExactly("tag1", "tag2");
    }

    @Test
    void shouldUploadImageAndUpdateCoverImage(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(postService, "uploadDir", tempDir.toString());

        Post post = Post.builder().id(1L).title("Post").content("c")
                .status(PostStatus.DRAFT).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", "fake-image-data".getBytes());

        ImageUploadResponse response = postService.uploadImage(1L, file, 1L);

        assertThat(response.getUrl()).isEqualTo("/api/uploads/posts/1/cover.jpg");
        assertThat(post.getCoverImage()).isEqualTo("/api/uploads/posts/1/cover.jpg");
        assertThat(tempDir.resolve("posts/1/cover.jpg")).exists();
    }

    @Test
    void shouldRejectNonImageFile() {
        Post post = Post.builder().id(1L).title("Post").content("c")
                .status(PostStatus.DRAFT).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "fake-data".getBytes());

        assertThatThrownBy(() -> postService.uploadImage(1L, file, 1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only JPEG and PNG images are allowed");
    }

    @Test
    void shouldRejectOversizedImage() {
        Post post = Post.builder().id(1L).title("Post").content("c")
                .status(PostStatus.DRAFT).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        byte[] largeData = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", largeData);

        assertThatThrownBy(() -> postService.uploadImage(1L, file, 1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Image must not exceed 5MB");
    }

    @Test
    void shouldRejectNullFile() {
        Post post = Post.builder().id(1L).title("Post").content("c")
                .status(PostStatus.DRAFT).author(author).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.uploadImage(1L, null, 1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("No image file provided");
    }
}
