package com.first.app.dto;

import com.first.app.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostSummary {

    private Long id;
    private String title;
    private String coverImage;
    private List<String> tags;
    private AuthResponse author;
    private int commentCount;
    private LocalDateTime createdAt;

    public static PostSummary from(Post post) {
        AuthResponse author = AuthResponse.builder()
                .id(post.getAuthor().getId())
                .name(post.getAuthor().getName())
                .build();

        return PostSummary.builder()
                .id(post.getId())
                .title(post.getTitle())
                .coverImage(post.getCoverImage())
                .tags(post.getTags())
                .author(author)
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
