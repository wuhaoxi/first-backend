package com.first.app.repository;

import com.first.app.entity.AttractionComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttractionCommentRepository extends JpaRepository<AttractionComment, Long> {

    Page<AttractionComment> findByAttractionIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(Long attractionId, Pageable pageable);

    Page<AttractionComment> findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(Long parentCommentId, Pageable pageable);

    long countByParentCommentIdAndDeletedFalse(Long parentCommentId);

    long countByAttractionIdAndDeletedFalse(Long attractionId);

    List<AttractionComment> findByAttractionIdAndDeletedFalse(Long attractionId);
}
