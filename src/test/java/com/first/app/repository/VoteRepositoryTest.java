package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.Vote;
import com.first.app.entity.VoteType;
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
class VoteRepositoryTest {

    @Autowired
    private VoteRepository voteRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Vote buildVote(Long postId, Long userId, VoteType voteType) {
        return Vote.builder()
                .postId(postId)
                .userId(userId)
                .voteType(voteType)
                .build();
    }

    @Test
    void shouldFindByPostIdAndUserId() {
        Vote vote = buildVote(1L, 1L, VoteType.UP);
        entityManager.persist(vote);
        entityManager.flush();

        Optional<Vote> found = voteRepository.findByPostIdAndUserId(1L, 1L);

        assertThat(found).isPresent();
        assertThat(found.get().getVoteType()).isEqualTo(VoteType.UP);
    }

    @Test
    void shouldCountByPostIdAndVoteType() {
        entityManager.persist(buildVote(1L, 1L, VoteType.UP));
        entityManager.persist(buildVote(1L, 2L, VoteType.UP));
        entityManager.persist(buildVote(1L, 3L, VoteType.DOWN));
        entityManager.persist(buildVote(2L, 1L, VoteType.UP));
        entityManager.flush();

        assertThat(voteRepository.countByPostIdAndVoteType(1L, VoteType.UP)).isEqualTo(2);
        assertThat(voteRepository.countByPostIdAndVoteType(1L, VoteType.DOWN)).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateVoteForSameUserAndPost() {
        entityManager.persist(buildVote(1L, 1L, VoteType.UP));
        entityManager.flush();

        Vote duplicate = buildVote(1L, 1L, VoteType.DOWN);

        assertThatThrownBy(() -> voteRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
