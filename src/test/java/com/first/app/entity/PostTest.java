package com.first.app.entity;

import com.first.app.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAutoGenerateIdAndSetDefaultsOnPersist() {
        Post post = Post.builder()
                .title("Test Post")
                .content("# Hello World")
                .authorId(1L)
                .build();

        entityManager.persist(post);
        entityManager.flush();

        assertThat(post.getId()).isNotNull();
        assertThat(post.getCreatedAt()).isNotNull();
        assertThat(post.getUpdatedAt()).isNotNull();
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getCommentCount()).isEqualTo(0);
        assertThat(post.getAuthorId()).isEqualTo(1L);
    }

    @Test
    void shouldPersistPostWithJsonTags() {
        Post post = Post.builder()
                .title("Tagged Post")
                .content("# Tagged")
                .authorId(2L)
                .tags(List.of("travel", "food"))
                .status(PostStatus.PUBLISHED)
                .build();

        entityManager.persist(post);
        entityManager.flush();

        Post found = entityManager.find(Post.class, post.getId());
        assertThat(found.getTags()).containsExactly("travel", "food");
        assertThat(found.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }
}
