package com.first.app.service;

import com.first.app.dto.PageResponse;
import com.first.app.dto.PostSummary;
import com.first.app.entity.Bookmark;
import com.first.app.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostService postService;

    @Transactional
    public boolean toggle(Long postId, Long userId) {
        postService.findByIdPublic(postId);

        Optional<Bookmark> existing = bookmarkRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            // Un-bookmark = hard delete — keeps the (post_id, user_id) unique constraint free for re-bookmarks
            bookmarkRepository.delete(existing.get());
            return false;
        }
        bookmarkRepository.save(Bookmark.builder()
                .postId(postId)
                .userId(userId)
                .build());
        return true;
    }

    public PageResponse<PostSummary> listBookmarks(Long userId, Pageable pageable) {
        return PageResponse.from(
                bookmarkRepository.findBookmarkedPosts(userId, pageable).map(PostSummary::from));
    }
}
