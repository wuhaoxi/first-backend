package com.first.app.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UpdateTodoRequest {

    @Size(max = 200, message = "title must not exceed 200 characters")
    private String title;

    @Size(max = 2000, message = "description must not exceed 2000 characters")
    private String description;

    private TodoPriority priority;

    private LocalDateTime dueDate;

    private List<String> tags;
}
