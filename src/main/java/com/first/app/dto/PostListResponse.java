package com.first.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PostListResponse {

    private List<PostSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private String nextCursor;
    private boolean hasMore;
}
