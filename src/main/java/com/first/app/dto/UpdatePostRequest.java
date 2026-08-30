package com.first.app.dto;

import com.first.app.entity.PostStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdatePostRequest {

    @Size(max = 200, message = "title must not exceed 200 characters")
    private String title;

    private String content;

    private List<String> tags;

    @Size(max = 500, message = "coverImage must not exceed 500 characters")
    private String coverImage;

    private PostStatus status;
}
