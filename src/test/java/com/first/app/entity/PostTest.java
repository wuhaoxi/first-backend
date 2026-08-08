package com.first.app.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PostTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAutoGenerateIdAndSetDefaultsOnPersist() {
        User author = User.builder()
                .name("Test Author")
                .email("author@test.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
        entityManager.persist(author);

        Post post = Post.builder()
                .title("Test Post")
                .content("# Hello World")
                .author(author)
                .build();

        entityManager.persist(post);
        entityManager.flush();

        assertThat(post.getId()).isNotNull();
        assertThat(post.getCreatedAt()).isNotNull();
        assertThat(post.getUpdatedAt()).isNotNull();
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getCommentCount()).isEqualTo(0);
        assertThat(post.getAuthor().getId()).isEqualTo(author.getId());
    }

    @Test
    void shouldPersistPostWithTags() {
        User author = User.builder()
                .name("Tag Author")
                .email("tag@test.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
        entityManager.persist(author);

        Post post = Post.builder()
                .title("Tagged Post")
                .content("# Tagged")
                .author(author)
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
