package com.first.app.repository;

import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByStatusOrderByCreatedAtDesc(PostStatus status);
    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
}
