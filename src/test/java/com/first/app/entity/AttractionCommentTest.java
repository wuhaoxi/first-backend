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
class AttractionCommentTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAutoGenerateIdAndSetDefaultsOnPersist() {
        AttractionComment comment = AttractionComment.builder()
                .attractionId(1L)
                .userId(1L)
                .content("Nice place")
                .build();

        entityManager.persist(comment);
        entityManager.flush();

        assertThat(comment.getId()).isNotNull();
        assertThat(comment.getCreatedAt()).isNotNull();
        assertThat(comment.getUpdatedAt()).isNotNull();
        assertThat(comment.isDeleted()).isFalse();
        assertThat(comment.getParentCommentId()).isNull();
    }

    @Test
    void shouldPersistReplyWithParentCommentId() {
        AttractionComment reply = AttractionComment.builder()
                .attractionId(1L)
                .userId(2L)
                .content("reply")
                .parentCommentId(5L)
                .build();

        entityManager.persist(reply);
        entityManager.flush();

        AttractionComment found = entityManager.find(AttractionComment.class, reply.getId());
        assertThat(found.getParentCommentId()).isEqualTo(5L);
        assertThat(found.getContent()).isEqualTo("reply");
        assertThat(found.isDeleted()).isFalse();
    }
}
