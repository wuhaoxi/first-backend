package com.first.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotPostResponse {
    private Long id;
    private String title;
    private String cityName;
    private int commentCount;
    private String createdAt;
}
