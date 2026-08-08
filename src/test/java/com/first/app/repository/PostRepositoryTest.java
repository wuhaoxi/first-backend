package com.first.app.repository;

import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User author1;
    private User author2;

    @BeforeEach
    void setUp() {
        author1 = User.builder()
                .name("Author One")
                .email("author1@test.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
        entityManager.persist(author1);

        author2 = User.builder()
                .name("Author Two")
                .email("author2@test.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
        entityManager.persist(author2);
        entityManager.flush();
    }

    @Test
    void shouldFindByStatusOrderByCreatedAtDesc() {
        Post draft = Post.builder()
                .title("Draft Post").content("# Draft").author(author1)
                .status(PostStatus.DRAFT).build();
        Post published1 = Post.builder()
                .title("First Published").content("# First").author(author1)
                .status(PostStatus.PUBLISHED).build();
        Post published2 = Post.builder()
                .title("Second Published").content("# Second").author(author2)
                .status(PostStatus.PUBLISHED).build();
        Post archived = Post.builder()
                .title("Archived Post").content("# Archived").author(author1)
                .status(PostStatus.ARCHIVED).build();

        entityManager.persist(draft);
        entityManager.persist(published1);
        entityManager.persist(published2);
        entityManager.persist(archived);
        entityManager.flush();

        List<Post> published = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);

        assertThat(published).hasSize(2);
        assertThat(published.get(0).getTitle()).isEqualTo("Second Published");
        assertThat(published.get(1).getTitle()).isEqualTo("First Published");
    }

    @Test
    void shouldFindByAuthorIdOrderByCreatedAtDesc() {
        Post post1 = Post.builder()
                .title("Author1 Post 1").content("# A1.1").author(author1)
                .status(PostStatus.PUBLISHED).build();
        Post post2 = Post.builder()
                .title("Author1 Post 2").content("# A1.2").author(author1)
                .status(PostStatus.DRAFT).build();
        Post post3 = Post.builder()
                .title("Author2 Post").content("# A2").author(author2)
                .status(PostStatus.PUBLISHED).build();

        entityManager.persist(post1);
        entityManager.persist(post2);
        entityManager.persist(post3);
        entityManager.flush();

        List<Post> author1Posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(author1.getId());

        assertThat(author1Posts).hasSize(2);
        assertThat(author1Posts.get(0).getTitle()).isEqualTo("Author1 Post 2");
        assertThat(author1Posts.get(1).getTitle()).isEqualTo("Author1 Post 1");
    }
}
