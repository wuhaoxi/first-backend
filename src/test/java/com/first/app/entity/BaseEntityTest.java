package com.first.app.entity;

import com.first.app.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BaseEntityTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAutoGenerateIdAndSetTimestampsOnPersist() {
        TestEntity entity = TestEntity.builder().name("test").build();

        entityManager.persist(entity);
        entityManager.flush();

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getCreatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    void shouldUpdateTimestampOnMerge() throws InterruptedException {
        TestEntity entity = TestEntity.builder().name("test").build();
        entityManager.persist(entity);
        entityManager.flush();

        LocalDateTime originalUpdatedAt = entity.getUpdatedAt();

        Thread.sleep(1); // ensure timestamp changes
        entity.setName("updated");
        entityManager.merge(entity);
        entityManager.flush();

        assertThat(entity.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void shouldNotAutoGenerateIdBeforePersist() {
        TestEntity entity = TestEntity.builder().name("test").build();

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }
}
