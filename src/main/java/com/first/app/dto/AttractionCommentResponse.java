package com.first.app.dto;

import com.first.app.entity.AttractionComment;
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
public class AttractionCommentResponse extends BaseResponse {

    private Long attractionId;
    private Long userId;
    private String content;
    private Long parentCommentId;

    /**
     * Number of visible (non-deleted) direct replies to this comment.
     * Populated by {@code AttractionCommentService} for list endpoints; 0 for freshly created comments.
     */
    private long replyCount;

    public static AttractionCommentResponse from(AttractionComment comment) {
        return AttractionCommentResponse.builder()
                .id(comment.getId())
                .attractionId(comment.getAttractionId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .parentCommentId(comment.getParentCommentId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
