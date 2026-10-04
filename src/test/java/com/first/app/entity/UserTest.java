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
class UserTest {

    @PersistenceContext
    private EntityManager entityManager;

    private User buildUser() {
        return User.builder()
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("$2a$10$hashed")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void shouldDefaultAvatarUrlToNullOnPersist() {
        User user = buildUser();

        entityManager.persist(user);
        entityManager.flush();

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getAvatarUrl()).isNull();
    }

    @Test
    void shouldPersistAvatarUrl() {
        User user = buildUser();
        user.setAvatarUrl("/api/uploads/avatars/1/avatar.jpg");

        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        User found = entityManager.find(User.class, user.getId());
        assertThat(found.getAvatarUrl()).isEqualTo("/api/uploads/avatars/1/avatar.jpg");
    }
}
