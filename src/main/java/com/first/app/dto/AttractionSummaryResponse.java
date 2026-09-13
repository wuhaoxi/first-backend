package com.first.app.dto;

import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AttractionSummaryResponse extends BaseResponse {

    private String slug;
    private String name;
    private String nameZh;
    private AttractionCategory category;
    private List<String> tags;
    private String city;
    private String citySlug;
    private String summary;
    private String coverImageUrl;
    private boolean bookingRequired;

    public static AttractionSummaryResponse from(Attraction attraction) {
        return AttractionSummaryResponse.builder()
                .id(attraction.getId())
                .createdAt(attraction.getCreatedAt())
                .slug(attraction.getSlug())
                .name(attraction.getName())
                .nameZh(attraction.getNameZh())
                .category(attraction.getCategory())
                .tags(attraction.getTags())
                .city(attraction.getCity())
                .citySlug(attraction.getCitySlug())
                .summary(attraction.getSummary())
                .coverImageUrl(attraction.getCoverImageUrl())
                .bookingRequired(attraction.isBookingRequired())
                .build();
    }
}
