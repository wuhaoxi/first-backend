package com.first.app.dto;

import com.first.app.entity.PostStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {

    @NotBlank(message = "title must not be blank")
    @Size(max = 200, message = "title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "content must not be blank")
    private String content;

    private List<String> tags;

    private String coverImage;

    private PostStatus status;
}
