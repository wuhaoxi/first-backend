package com.first.app.service;

import com.first.app.dto.PageResponse;
import com.first.app.dto.PostSummary;
import com.first.app.entity.Bookmark;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.BookmarkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private PostService postService;

    @InjectMocks
    private BookmarkService bookmarkService;

    private static final Long POST_ID = 1L;
    private static final Long USER_ID = 1L;

    private Post buildPublishedPost(Long id, String title) {
        return Post.builder()
                .id(id).title(title).content("# Hello")
                .status(PostStatus.PUBLISHED).authorId(1L)
                .build();
    }

    @Test
    void toggle_whenAbsent_savesAndReturnsTrue() {
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPublishedPost(POST_ID, "Test Post"));
        when(bookmarkRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        boolean bookmarked = bookmarkService.toggle(POST_ID, USER_ID);

        assertThat(bookmarked).isTrue();
        verify(bookmarkRepository).save(any(Bookmark.class));
        verify(bookmarkRepository, never()).delete(any(Bookmark.class));
    }

    @Test
    void toggle_whenPresent_deletesAndReturnsFalse() {
        Bookmark existing = Bookmark.builder().id(9L).postId(POST_ID).userId(USER_ID).build();
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPublishedPost(POST_ID, "Test Post"));
        when(bookmarkRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(existing));

        boolean bookmarked = bookmarkService.toggle(POST_ID, USER_ID);

        assertThat(bookmarked).isFalse();
        verify(bookmarkRepository).delete(existing);
        verify(bookmarkRepository, never()).save(any(Bookmark.class));
    }

    @Test
    void toggle_postNotFound_throws404() {
        when(postService.findByIdPublic(POST_ID))
                .thenThrow(new ResourceNotFoundException("Post not found with id: " + POST_ID));

        assertThatThrownBy(() -> bookmarkService.toggle(POST_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listBookmarks_returnsPageResponseOfPostSummary() {
        Post post1 = buildPublishedPost(1L, "Post 1");
        Post post2 = buildPublishedPost(2L, "Post 2");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Post> page = new PageImpl<>(List.of(post1, post2), pageable, 2);
        when(bookmarkRepository.findBookmarkedPosts(USER_ID, pageable)).thenReturn(page);

        PageResponse<PostSummary> response = bookmarkService.listBookmarks(USER_ID, pageable);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("Post 1");
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }
}
