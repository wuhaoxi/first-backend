package com.first.app.repository;

import com.first.app.entity.Bookmark;
import com.first.app.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByPostIdAndUserId(Long postId, Long userId);

    @Query("SELECT p FROM Bookmark b JOIN Post p ON b.postId = p.id WHERE b.userId = :userId ORDER BY b.createdAt DESC")
    Page<Post> findBookmarkedPosts(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT b.postId, COUNT(b) FROM Bookmark b WHERE b.postId IN :postIds GROUP BY b.postId")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);
}
