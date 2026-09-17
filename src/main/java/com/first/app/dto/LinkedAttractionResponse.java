package com.first.app.dto;

import com.first.app.entity.Attraction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A published attraction linked to a post, in junction creation order.
 * Deliberately lean — only the reference fields the API contract exposes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkedAttractionResponse {

    private Long id;
    private String slug;
    private String name;
    private String nameZh;

    public static LinkedAttractionResponse from(Attraction attraction) {
        return LinkedAttractionResponse.builder()
                .id(attraction.getId())
                .slug(attraction.getSlug())
                .name(attraction.getName())
                .nameZh(attraction.getNameZh())
                .build();
    }
}
