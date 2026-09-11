package com.first.app.dto;

import com.first.app.entity.Comment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CommentResponse extends BaseResponse {

    private Long postId;
    private Long userId;
    private String content;
    private Long parentCommentId;

    /**
     * Number of visible (non-deleted) direct replies to this comment.
     * Populated by {@code CommentService} for list endpoints; 0 for freshly created comments.
     */
    private long replyCount;

    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .parentCommentId(comment.getParentCommentId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
