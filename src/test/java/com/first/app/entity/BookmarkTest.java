package com.first.app.entity;

import com.first.app.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookmarkTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAutoGenerateIdAndTimestampsOnPersist() {
        Bookmark bookmark = Bookmark.builder()
                .postId(1L)
                .userId(1L)
                .build();

        entityManager.persist(bookmark);
        entityManager.flush();

        assertThat(bookmark.getId()).isNotNull();
        assertThat(bookmark.getCreatedAt()).isNotNull();
        assertThat(bookmark.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldPersistBookmarkFields() {
        Bookmark bookmark = Bookmark.builder()
                .postId(2L)
                .userId(3L)
                .build();

        entityManager.persist(bookmark);
        entityManager.flush();

        Bookmark found = entityManager.find(Bookmark.class, bookmark.getId());
        assertThat(found.getPostId()).isEqualTo(2L);
        assertThat(found.getUserId()).isEqualTo(3L);
    }
}