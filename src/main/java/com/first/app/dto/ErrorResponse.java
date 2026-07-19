package com.first.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String message;
    private int status;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private List<Map<String, String>> errors;

    public static ErrorResponse of(String message, int status) {
        return ErrorResponse.builder()
                .message(message)
                .status(status)
                .build();
    }
}
