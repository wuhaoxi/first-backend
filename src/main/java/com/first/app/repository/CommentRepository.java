package com.first.app.repository;

import com.first.app.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(Long postId, Pageable pageable);

    Page<Comment> findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(Long parentCommentId, Pageable pageable);

    List<Comment> findByPostIdAndDeletedFalse(Long postId);
}
