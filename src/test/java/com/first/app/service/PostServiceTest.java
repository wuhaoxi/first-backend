package com.first.app.service;

import com.first.app.dto.CreatePostRequest;
import com.first.app.dto.ImageUploadResponse;
import com.first.app.dto.PostListResponse;
import com.first.app.dto.PostSummary;
import com.first.app.dto.UpdatePostRequest;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.PostRepository;
import com.first.app.util.PostCursorCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostStatsEnricher postStatsEnricher;

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

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 3, 12, 0, 0);

    private Post buildPublishedPost(Long id, LocalDateTime createdAt) {
        return Post.builder()
                .id(id).title("Post " + id).content("content")
                .status(PostStatus.PUBLISHED).authorId(AUTHOR_ID)
                .createdAt(createdAt)
                .build();
    }

    private Post buildCommentedPost(Long id, int commentCount) {
        return Post.builder()
                .id(id).title("Post " + id).content("content")
                .status(PostStatus.PUBLISHED).authorId(AUTHOR_ID)
                .commentCount(commentCount)
                .build();
    }

    private List<Post> buildPublishedPosts(int count, long topId, LocalDateTime topTime) {
        return LongStream.range(0, count)
                .mapToObj(i -> buildPublishedPost(topId - i, topTime.minusMinutes(i)))
                .toList();
    }

    private static List<Long> idsDescending(long from, long to) {
        return LongStream.iterate(from, i -> i - 1)
                .limit(from - to + 1)
                .boxed()
                .toList();
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
    void create_shouldRejectOversizedByteContent() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Oversized");
        request.setContent("文".repeat(25000)); // 25,000 CJK chars = 75,000 UTF-8 bytes > 60,000 limit

        assertThatThrownBy(() -> postService.create(request, AUTHOR_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("content");
    }

    @Test
    void update_shouldRejectOversizedByteContent() {
        Post existing = buildPost(1L, PostStatus.DRAFT, AUTHOR_ID);
        UpdatePostRequest request = new UpdatePostRequest();
        request.setContent("文".repeat(25000)); // 75,000 UTF-8 bytes > 60,000 limit

        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> postService.update(1L, request, AUTHOR_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("content");
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

    @Test
    void findList_defaultsToLatestOffsetEnvelope() {
        Post newer = buildPublishedPost(2L, BASE_TIME);
        Post older = buildPublishedPost(1L, BASE_TIME.minusMinutes(1));
        Pageable pageable = PageRequest.of(0, 20);
        when(postRepository.findByStatusOrderByCreatedAtDescIdDesc(PostStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(newer, older), pageable, 2));

        PostListResponse response = postService.findList(null, null, null, null);

        assertThat(response.getContent()).extracting(PostSummary::getId).containsExactly(2L, 1L);
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isHasMore()).isFalse();
        assertThat(response.getNextCursor()).isNull();
        verify(postStatsEnricher).enrich(any());
    }

    @Test
    void findList_offsetHasMoreUsesPagePlusOneMath() {
        List<Post> firstWindow = buildPublishedPosts(20, 20L, BASE_TIME);
        Pageable firstPage = PageRequest.of(0, 20);
        when(postRepository.findByStatusOrderByCreatedAtDescIdDesc(PostStatus.PUBLISHED, firstPage))
                .thenReturn(new PageImpl<>(firstWindow, firstPage, 45));

        PostListResponse first = postService.findList("latest", 0, 20, null);

        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.isHasMore()).isTrue();
        assertThat(first.getNextCursor()).isNotNull();

        List<Post> lastWindow = buildPublishedPosts(5, 5L, BASE_TIME.minusMinutes(40));
        Pageable lastPage = PageRequest.of(2, 20);
        when(postRepository.findByStatusOrderByCreatedAtDescIdDesc(PostStatus.PUBLISHED, lastPage))
                .thenReturn(new PageImpl<>(lastWindow, lastPage, 45));

        PostListResponse last = postService.findList("latest", 2, 20, null);

        assertThat(last.isHasMore()).isFalse();
        assertThat(last.getNextCursor()).isNull();
    }

    @Test
    void findList_cursorFlow_pagesStrictlyAfterCursorWithoutDuplicates() {
        String startCursor = PostCursorCodec.encode(BASE_TIME.plusMinutes(1), 32L);
        List<Post> firstWindow = buildPublishedPosts(21, 31L, BASE_TIME); // ids 31..11, probe +1
        when(postRepository.findLatestBeforeCursor(eq(PostStatus.PUBLISHED), any(), any(),
                eq(PageRequest.of(0, 21))))
                .thenReturn(firstWindow);
        when(postRepository.countByStatus(PostStatus.PUBLISHED)).thenReturn(42L);

        PostListResponse first = postService.findList("latest", 5, 20, startCursor);

        assertThat(first.getContent()).extracting(PostSummary::getId)
                .containsExactlyElementsOf(idsDescending(31, 12));
        assertThat(first.getPage()).isZero(); // page ignored in cursor mode
        assertThat(first.getSize()).isEqualTo(20);
        assertThat(first.getTotalElements()).isEqualTo(42);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.isHasMore()).isTrue();
        assertThat(first.getNextCursor()).isNotNull();

        PostCursorCodec.Cursor decoded = PostCursorCodec.decode(first.getNextCursor());
        assertThat(decoded.createdAt()).isEqualTo(BASE_TIME.minusMinutes(19));
        assertThat(decoded.id()).isEqualTo(12L);

        List<Post> secondWindow = buildPublishedPosts(11, 11L, BASE_TIME.minusMinutes(20));
        when(postRepository.findLatestBeforeCursor(eq(PostStatus.PUBLISHED),
                eq(decoded.createdAt()), eq(decoded.id()), eq(PageRequest.of(0, 21))))
                .thenReturn(secondWindow);

        PostListResponse second = postService.findList("latest", 3, 20, first.getNextCursor());

        assertThat(second.getContent()).extracting(PostSummary::getId)
                .containsExactlyElementsOf(idsDescending(11, 1));
        assertThat(second.getPage()).isZero();
        assertThat(second.isHasMore()).isFalse();
        assertThat(second.getNextCursor()).isNull();
    }

    @Test
    void findList_upvotes_hydratesInIdPageOrderAndNeverEmitsCursor() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postRepository.findIdPageByUpvoteCount(PostStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(3L, 1L, 2L), pageable, 50));
        when(postRepository.findAllById(List.of(3L, 1L, 2L)))
                .thenReturn(List.of(
                        buildPublishedPost(1L, BASE_TIME.minusMinutes(3)),
                        buildPublishedPost(3L, BASE_TIME),
                        buildPublishedPost(2L, BASE_TIME.minusMinutes(9))));

        PostListResponse response = postService.findList("upvotes", 0, 20, null);

        assertThat(response.getContent()).extracting(PostSummary::getId)
                .containsExactly(3L, 1L, 2L);
        assertThat(response.getTotalElements()).isEqualTo(50);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isHasMore()).isTrue();
        assertThat(response.getNextCursor()).isNull();
        verify(postStatsEnricher).enrich(any());
    }

    @Test
    void findList_upvotes_emptyIdPage_skipsHydration() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postRepository.findIdPageByUpvoteCount(PostStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PostListResponse response = postService.findList("upvotes", 0, 20, null);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isHasMore()).isFalse();
        assertThat(response.getNextCursor()).isNull();
        verify(postRepository, never()).findAllById(any());
    }

    @Test
    void findList_comments_ordersByCommentCountWithoutCursor() {
        Post mostCommented = buildCommentedPost(1L, 9);
        Post lessCommented = buildCommentedPost(2L, 5);
        Pageable pageable = PageRequest.of(0, 20);
        when(postRepository.findByStatusOrderByCommentCountDescIdDesc(PostStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(mostCommented, lessCommented), pageable, 26));

        PostListResponse response = postService.findList("comments", 0, null, null);

        assertThat(response.getContent()).extracting(PostSummary::getId).containsExactly(1L, 2L);
        assertThat(response.getContent()).extracting(PostSummary::getCommentCount)
                .containsExactly(9, 5);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.isHasMore()).isTrue();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void findList_unknownSort_throws400() {
        assertThatThrownBy(() -> postService.findList("trending", null, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid sort: trending");
    }

    @Test
    void findList_sizeBelowOne_throws400() {
        assertThatThrownBy(() -> postService.findList(null, 0, 0, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid size");
    }

    @Test
    void findList_sizeAboveMax_isClampedTo100() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(postRepository.findByStatusOrderByCreatedAtDescIdDesc(eq(PostStatus.PUBLISHED),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        PostListResponse response = postService.findList("latest", 0, 999, null);

        verify(postRepository).findByStatusOrderByCreatedAtDescIdDesc(eq(PostStatus.PUBLISHED),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(response.getSize()).isEqualTo(100);
        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    void findList_negativePage_throws400() {
        assertThatThrownBy(() -> postService.findList("latest", -1, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid page");
    }

    @Test
    void findList_malformedCursor_throws400() {
        assertThatThrownBy(() -> postService.findList("latest", null, null, "not-base64!!"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid cursor");
    }

    @Test
    void findList_cursorWithNonLatestSort_throws400() {
        String cursor = PostCursorCodec.encode(BASE_TIME, 42L);

        assertThatThrownBy(() -> postService.findList("upvotes", null, null, cursor))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cursor is only supported with sort=latest");
    }

    @Test
    void findList_blankCursor_isTreatedAsAbsent() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postRepository.findByStatusOrderByCreatedAtDescIdDesc(PostStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PostListResponse response = postService.findList("latest", 0, 20, "   ");

        assertThat(response.getPage()).isZero();
        verify(postRepository, never()).findLatestBeforeCursor(any(), any(), any(), any());
    }
}
