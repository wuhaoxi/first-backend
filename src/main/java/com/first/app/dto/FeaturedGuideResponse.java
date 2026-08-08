package com.first.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedGuideResponse {
    private Long id;
    private String title;
    private String cityName;
    private String coverImageUrl;
    private String recommendation;
    private String slug;
}
