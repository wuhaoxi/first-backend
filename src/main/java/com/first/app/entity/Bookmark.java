package com.first.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "bookmarks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bookmarks_post_user", columnNames = {"post_id", "user_id"})
}, indexes = {
        @Index(name = "idx_bookmarks_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Bookmark extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
