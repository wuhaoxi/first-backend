package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.AttractionComment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AttractionCommentRepositoryTest {

    @Autowired
    private AttractionCommentRepository attractionCommentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private AttractionComment buildComment(Long attractionId, Long userId, Long parentId, String content) {
        return AttractionComment.builder()
                .attractionId(attractionId)
                .userId(userId)
                .parentCommentId(parentId)
                .content(content)
                .build();
    }

    @Test
    void shouldFindTopLevelCommentsNotDeletedOrderedAsc() {
        AttractionComment top1 = buildComment(1L, 1L, null, "top 1");
        AttractionComment top2 = buildComment(1L, 2L, null, "top 2");
        AttractionComment reply = buildComment(1L, 1L, 10L, "reply");
        AttractionComment deletedTop = buildComment(1L, 3L, null, "deleted top");
        deletedTop.setDeleted(true);

        entityManager.persist(top1);
        entityManager.persist(top2);
        entityManager.persist(reply);
        entityManager.persist(deletedTop);
        entityManager.flush();

        Page<AttractionComment> result = attractionCommentRepository
                .findByAttractionIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(
                        1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(AttractionComment::getContent)
                .containsExactly("top 1", "top 2");
    }

    @Test
    void shouldFindRepliesNotDeletedOrderedAsc() {
        AttractionComment reply1 = buildComment(1L, 1L, 10L, "reply 1");
        AttractionComment reply2 = buildComment(1L, 2L, 10L, "reply 2");
        AttractionComment otherParent = buildComment(1L, 1L, 99L, "other parent");
        AttractionComment deletedReply = buildComment(1L, 3L, 10L, "deleted reply");
        deletedReply.setDeleted(true);

        entityManager.persist(reply1);
        entityManager.persist(reply2);
        entityManager.persist(otherParent);
        entityManager.persist(deletedReply);
        entityManager.flush();

        Page<AttractionComment> result = attractionCommentRepository
                .findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(
                        10L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(AttractionComment::getContent)
                .containsExactly("reply 1", "reply 2");
    }

    @Test
    void shouldCountNonDeletedRepliesByParent() {
        entityManager.persist(buildComment(1L, 1L, 10L, "reply 1"));
        entityManager.persist(buildComment(1L, 2L, 10L, "reply 2"));
        entityManager.persist(buildComment(1L, 1L, 99L, "other parent"));
        AttractionComment deletedReply = buildComment(1L, 3L, 10L, "deleted reply");
        deletedReply.setDeleted(true);
        entityManager.persist(deletedReply);
        entityManager.flush();

        assertThat(attractionCommentRepository.countByParentCommentIdAndDeletedFalse(10L)).isEqualTo(2);
    }

    @Test
    void shouldCountAndListNonDeletedCommentsByAttraction() {
        AttractionComment kept = buildComment(1L, 1L, null, "kept");
        AttractionComment deleted = buildComment(1L, 2L, null, "deleted");
        deleted.setDeleted(true);
        AttractionComment otherAttraction = buildComment(2L, 1L, null, "other attraction");

        entityManager.persist(kept);
        entityManager.persist(deleted);
        entityManager.persist(otherAttraction);
        entityManager.flush();

        assertThat(attractionCommentRepository.countByAttractionIdAndDeletedFalse(1L)).isEqualTo(1);
        assertThat(attractionCommentRepository.findByAttractionIdAndDeletedFalse(1L))
                .extracting(AttractionComment::getContent)
                .containsExactly("kept");
    }
}
