package com.first.app.dto;

import com.first.app.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PostSummary extends BaseResponse {

    private String title;
    private String coverImage;
    private List<String> tags;
    private Long authorId;
    private int commentCount;

    public static PostSummary from(Post post) {
        return PostSummary.builder()
                .id(post.getId())
                .title(post.getTitle())
                .coverImage(post.getCoverImage())
                .tags(post.getTags())
                .authorId(post.getAuthorId())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
