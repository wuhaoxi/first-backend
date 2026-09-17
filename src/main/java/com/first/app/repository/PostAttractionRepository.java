package com.first.app.repository;

import com.first.app.entity.PostAttraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostAttractionRepository extends JpaRepository<PostAttraction, Long> {

    List<PostAttraction> findByPostIdOrderByIdAsc(Long postId);

    void deleteByPostId(Long postId);
}
