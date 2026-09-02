package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.Bookmark;
import com.first.app.entity.Post;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookmarkRepositoryTest {

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Post buildPost(String title) {
        return Post.builder()
                .title(title)
                .content("content of " + title)
                .authorId(1L)
                .build();
    }

    private Bookmark buildBookmark(Long postId, Long userId) {
        return Bookmark.builder()
                .postId(postId)
                .userId(userId)
                .build();
    }

    @Test
    void shouldFindByPostIdAndUserId() {
        entityManager.persist(buildBookmark(1L, 1L));
        entityManager.flush();

        Optional<Bookmark> found = bookmarkRepository.findByPostIdAndUserId(1L, 1L);

        assertThat(found).isPresent();
        assertThat(found.get().getPostId()).isEqualTo(1L);
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnBookmarkedPostsOrderedByBookmarkCreatedAtDesc() {
        Post post1 = buildPost("Post 1");
        Post post2 = buildPost("Post 2");
        Post post3 = buildPost("Post 3");
        entityManager.persist(post1);
        entityManager.persist(post2);
        entityManager.persist(post3);
        entityManager.flush();

        // User 1 bookmarks Post 1 first, then Post 2 (newest bookmark first in results)
        entityManager.persist(buildBookmark(post1.getId(), 1L));
        entityManager.flush();
        entityManager.persist(buildBookmark(post2.getId(), 1L));
        entityManager.flush();
        // Another user's bookmark must not leak in
        entityManager.persist(buildBookmark(post3.getId(), 2L));
        entityManager.flush();

        Page<Post> result = bookmarkRepository.findBookmarkedPosts(1L, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Post::getTitle)
                .containsExactly("Post 2", "Post 1");
    }

    @Test
    void shouldRejectDuplicateBookmarkForSameUserAndPost() {
        entityManager.persist(buildBookmark(1L, 1L));
        entityManager.flush();

        Bookmark duplicate = buildBookmark(1L, 1L);

        assertThatThrownBy(() -> bookmarkRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
