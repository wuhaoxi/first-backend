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
class VoteTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAutoGenerateIdAndTimestampsOnPersist() {
        Vote vote = Vote.builder()
                .postId(1L)
                .userId(1L)
                .voteType(VoteType.UP)
                .build();

        entityManager.persist(vote);
        entityManager.flush();

        assertThat(vote.getId()).isNotNull();
        assertThat(vote.getCreatedAt()).isNotNull();
        assertThat(vote.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldPersistVoteTypeAsString() {
        Vote vote = Vote.builder()
                .postId(2L)
                .userId(3L)
                .voteType(VoteType.DOWN)
                .build();

        entityManager.persist(vote);
        entityManager.flush();

        Vote found = entityManager.find(Vote.class, vote.getId());
        assertThat(found.getVoteType()).isEqualTo(VoteType.DOWN);
        assertThat(found.getPostId()).isEqualTo(2L);
        assertThat(found.getUserId()).isEqualTo(3L);
    }
}
