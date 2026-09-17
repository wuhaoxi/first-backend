package com.first.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "attraction_comments", indexes = {
        @Index(name = "idx_attraction_comments_attraction_parent", columnList = "attraction_id, parent_comment_id"),
        @Index(name = "idx_attraction_comments_parent_id", columnList = "parent_comment_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AttractionComment extends BaseEntity {

    @Column(name = "attraction_id", nullable = false)
    private Long attractionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotBlank(message = "content must not be blank")
    @Size(max = 2000, message = "content must not exceed 2000 characters")
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;
}
