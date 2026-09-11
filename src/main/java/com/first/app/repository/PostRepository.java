package com.first.app.repository;

import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByStatusOrderByCreatedAtDesc(PostStatus status);
    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    Page<Post> findByStatusOrderByCreatedAtDescIdDesc(PostStatus status, Pageable pageable);

    Page<Post> findByStatusOrderByCommentCountDescIdDesc(PostStatus status, Pageable pageable);

    long countByStatus(PostStatus status);

    @Query("SELECT p FROM Post p WHERE p.status = :status "
            + "AND (p.createdAt < :createdAt OR (p.createdAt = :createdAt AND p.id < :id)) "
            + "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findLatestBeforeCursor(@Param("status") PostStatus status,
                                      @Param("createdAt") LocalDateTime createdAt,
                                      @Param("id") Long id,
                                      Pageable pageable);

    @Query(value = "SELECT p.id FROM Post p "
            + "LEFT JOIN Vote v ON v.postId = p.id AND v.voteType = com.first.app.entity.VoteType.UP "
            + "WHERE p.status = :status "
            + "GROUP BY p.id "
            + "ORDER BY COUNT(v.id) DESC, p.id DESC",
            countQuery = "SELECT COUNT(DISTINCT p.id) FROM Post p WHERE p.status = :status")
    Page<Long> findIdPageByUpvoteCount(@Param("status") PostStatus status, Pageable pageable);
}
