package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.Comment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Comment buildComment(Long postId, Long userId, Long parentId, String content) {
        return Comment.builder()
                .postId(postId)
                .userId(userId)
                .parentCommentId(parentId)
                .content(content)
                .build();
    }

    @Test
    void shouldFindTopLevelCommentsNotDeletedOrderedAsc() {
        Comment top1 = buildComment(1L, 1L, null, "top 1");
        Comment top2 = buildComment(1L, 2L, null, "top 2");
        Comment reply = buildComment(1L, 1L, 10L, "reply");
        Comment deletedTop = buildComment(1L, 3L, null, "deleted top");
        deletedTop.setDeleted(true);

        entityManager.persist(top1);
        entityManager.persist(top2);
        entityManager.persist(reply);
        entityManager.persist(deletedTop);
        entityManager.flush();

        Page<Comment> result = commentRepository
                .findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(
                        1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Comment::getContent)
                .containsExactly("top 1", "top 2");
    }

    @Test
    void shouldFindRepliesNotDeletedOrderedAsc() {
        Comment reply1 = buildComment(1L, 1L, 10L, "reply 1");
        Comment reply2 = buildComment(1L, 2L, 10L, "reply 2");
        Comment otherParent = buildComment(1L, 1L, 99L, "other parent");
        Comment deletedReply = buildComment(1L, 3L, 10L, "deleted reply");
        deletedReply.setDeleted(true);

        entityManager.persist(reply1);
        entityManager.persist(reply2);
        entityManager.persist(otherParent);
        entityManager.persist(deletedReply);
        entityManager.flush();

        Page<Comment> result = commentRepository
                .findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(
                        10L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Comment::getContent)
                .containsExactly("reply 1", "reply 2");
    }

    @Test
    void shouldFindAllNonDeletedCommentsByPostForCascadeTraversal() {
        Comment top = buildComment(1L, 1L, null, "top");
        entityManager.persist(top);
        entityManager.flush();

        Comment reply = buildComment(1L, 2L, top.getId(), "reply");
        Comment deleted = buildComment(1L, 3L, null, "deleted");
        deleted.setDeleted(true);

        entityManager.persist(reply);
        entityManager.persist(deleted);
        entityManager.flush();

        List<Comment> result = commentRepository.findByPostIdAndDeletedFalse(1L);

        assertThat(result).extracting(Comment::getContent)
                .containsExactlyInAnyOrder("top", "reply");
    }
}
