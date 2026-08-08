package com.first.app.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_posts_status_created", columnList = "status, created_at"),
    @Index(name = "idx_posts_author_id", columnList = "author_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Post extends BaseEntity {

    @NotBlank(message = "title must not be blank")
    @Size(max = 200, message = "title must not exceed 200 characters")
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank(message = "content must not be blank")
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 500)
    private String coverImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private PostStatus status = PostStatus.DRAFT;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Builder.Default
    private int commentCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
