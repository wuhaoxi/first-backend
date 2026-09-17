package com.first.app.dto;

import com.first.app.entity.City;
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
public class CityResponse extends CitySummaryResponse {

    private String description;
    private long attractionCount;

    public static CityResponse from(City city, long attractionCount) {
        return CityResponse.builder()
                .id(city.getId())
                .createdAt(city.getCreatedAt())
                .updatedAt(city.getUpdatedAt())
                .slug(city.getSlug())
                .name(city.getName())
                .nameZh(city.getNameZh())
                .coverImageUrl(city.getCoverImageUrl())
                .bestSeason(city.getBestSeason())
                .description(city.getDescription())
                .attractionCount(attractionCount)
                .build();
    }
}
