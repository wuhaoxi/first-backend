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
class AttractionFavoriteTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAutoGenerateIdAndTimestampsOnPersist() {
        AttractionFavorite favorite = AttractionFavorite.builder()
                .attractionId(1L)
                .userId(1L)
                .build();

        entityManager.persist(favorite);
        entityManager.flush();

        assertThat(favorite.getId()).isNotNull();
        assertThat(favorite.getCreatedAt()).isNotNull();
        assertThat(favorite.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldPersistFavoriteFields() {
        AttractionFavorite favorite = AttractionFavorite.builder()
                .attractionId(2L)
                .userId(3L)
                .build();

        entityManager.persist(favorite);
        entityManager.flush();

        AttractionFavorite found = entityManager.find(AttractionFavorite.class, favorite.getId());
        assertThat(found.getAttractionId()).isEqualTo(2L);
        assertThat(found.getUserId()).isEqualTo(3L);
    }
}
