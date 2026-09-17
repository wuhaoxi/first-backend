package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.AttractionFavorite;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AttractionFavoriteRepositoryTest {

    @Autowired
    private AttractionFavoriteRepository attractionFavoriteRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private AttractionFavorite buildFavorite(Long attractionId, Long userId) {
        return AttractionFavorite.builder()
                .attractionId(attractionId)
                .userId(userId)
                .build();
    }

    @Test
    void shouldFindByAttractionIdAndUserId() {
        entityManager.persist(buildFavorite(1L, 1L));
        entityManager.flush();

        Optional<AttractionFavorite> found = attractionFavoriteRepository.findByAttractionIdAndUserId(1L, 1L);

        assertThat(found).isPresent();
        assertThat(found.get().getAttractionId()).isEqualTo(1L);
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void shouldCountByAttractionId() {
        entityManager.persist(buildFavorite(1L, 1L));
        entityManager.persist(buildFavorite(1L, 2L));
        entityManager.persist(buildFavorite(2L, 1L));
        entityManager.flush();

        assertThat(attractionFavoriteRepository.countByAttractionId(1L)).isEqualTo(2);
        assertThat(attractionFavoriteRepository.countByAttractionId(3L)).isZero();
    }

    @Test
    void shouldRejectDuplicateFavoriteForSameUserAndAttraction() {
        entityManager.persist(buildFavorite(1L, 1L));
        entityManager.flush();

        AttractionFavorite duplicate = buildFavorite(1L, 1L);

        assertThatThrownBy(() -> attractionFavoriteRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
