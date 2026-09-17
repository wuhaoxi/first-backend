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
public class CitySummaryResponse extends BaseResponse {

    private String slug;
    private String name;
    private String nameZh;
    private String coverImageUrl;
    private String bestSeason;

    public static CitySummaryResponse from(City city) {
        return CitySummaryResponse.builder()
                .id(city.getId())
                .createdAt(city.getCreatedAt())
                .slug(city.getSlug())
                .name(city.getName())
                .nameZh(city.getNameZh())
                .coverImageUrl(city.getCoverImageUrl())
                .bestSeason(city.getBestSeason())
                .build();
    }
}
