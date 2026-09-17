package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.PostAttraction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostAttractionRepositoryTest {

    @Autowired
    private PostAttractionRepository postAttractionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private PostAttraction buildLink(Long postId, Long attractionId) {
        return PostAttraction.builder()
                .postId(postId)
                .attractionId(attractionId)
                .build();
    }

    @Test
    void shouldFindByPostIdInCreationOrder() {
        entityManager.persist(buildLink(7L, 3L));
        entityManager.flush();
        entityManager.persist(buildLink(7L, 1L));
        entityManager.flush();
        // Another post's link must not leak in
        entityManager.persist(buildLink(8L, 1L));
        entityManager.flush();

        List<PostAttraction> links = postAttractionRepository.findByPostIdOrderByIdAsc(7L);

        assertThat(links).extracting(PostAttraction::getAttractionId).containsExactly(3L, 1L);
    }

    @Test
    void shouldRejectDuplicatePostAttractionPair() {
        entityManager.persist(buildLink(7L, 3L));
        entityManager.flush();

        PostAttraction duplicate = buildLink(7L, 3L);

        assertThatThrownBy(() -> postAttractionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldDeleteAllLinksForPost() {
        entityManager.persist(buildLink(7L, 3L));
        entityManager.persist(buildLink(7L, 1L));
        entityManager.persist(buildLink(8L, 1L));
        entityManager.flush();

        postAttractionRepository.deleteByPostId(7L);
        entityManager.flush();

        assertThat(postAttractionRepository.findByPostIdOrderByIdAsc(7L)).isEmpty();
        assertThat(postAttractionRepository.findByPostIdOrderByIdAsc(8L)).hasSize(1);
    }
}
