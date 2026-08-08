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
public class PostResponse extends BaseResponse {

    private String title;
    private String content;
    private String coverImage;
    private List<String> tags;
    private String status;
    private Long authorId;
    private int commentCount;

    public static PostResponse from(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .coverImage(post.getCoverImage())
                .tags(post.getTags())
                .status(post.getStatus().name())
                .authorId(post.getAuthorId())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
