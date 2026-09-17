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
class PostAttractionTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAutoGenerateIdAndTimestampsOnPersist() {
        PostAttraction link = PostAttraction.builder()
                .postId(7L)
                .attractionId(3L)
                .build();

        entityManager.persist(link);
        entityManager.flush();

        assertThat(link.getId()).isNotNull();
        assertThat(link.getCreatedAt()).isNotNull();
        assertThat(link.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldPersistPostIdAndAttractionId() {
        PostAttraction link = PostAttraction.builder()
                .postId(7L)
                .attractionId(1L)
                .build();

        entityManager.persist(link);
        entityManager.flush();

        PostAttraction found = entityManager.find(PostAttraction.class, link.getId());
        assertThat(found.getPostId()).isEqualTo(7L);
        assertThat(found.getAttractionId()).isEqualTo(1L);
    }
}
