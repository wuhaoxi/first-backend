package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldFindByStatusOrderByCreatedAtDesc() {
        Post draft = Post.builder()
                .title("Draft Post").content("# Draft").authorId(1L)
                .status(PostStatus.DRAFT).build();
        Post published1 = Post.builder()
                .title("First Published").content("# First").authorId(1L)
                .status(PostStatus.PUBLISHED).build();
        Post published2 = Post.builder()
                .title("Second Published").content("# Second").authorId(2L)
                .status(PostStatus.PUBLISHED).build();

        entityManager.persist(draft);
        entityManager.persist(published1);
        entityManager.persist(published2);
        entityManager.flush();

        List<Post> published = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);

        assertThat(published).hasSize(2);
        assertThat(published.get(0).getTitle()).isEqualTo("Second Published");
        assertThat(published.get(1).getTitle()).isEqualTo("First Published");
    }

    @Test
    void shouldFindByAuthorIdOrderByCreatedAtDesc() {
        Post post1 = Post.builder()
                .title("Author1 Post 1").content("# A1.1").authorId(1L)
                .status(PostStatus.PUBLISHED).build();
        Post post2 = Post.builder()
                .title("Author1 Post 2").content("# A1.2").authorId(1L)
                .status(PostStatus.DRAFT).build();
        Post post3 = Post.builder()
                .title("Author2 Post").content("# A2").authorId(2L)
                .status(PostStatus.PUBLISHED).build();

        entityManager.persist(post1);
        entityManager.persist(post2);
        entityManager.persist(post3);
        entityManager.flush();

        List<Post> author1Posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(1L);

        assertThat(author1Posts).hasSize(2);
        assertThat(author1Posts.get(0).getTitle()).isEqualTo("Author1 Post 2");
        assertThat(author1Posts.get(1).getTitle()).isEqualTo("Author1 Post 1");
    }
}
