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
public class AttractionResponse extends BaseResponse {

    private String slug;
    private String name;
    private String nameZh;
    private AttractionCategory category;
    private List<String> tags;
    private String city;
    private String citySlug;
    private String province;
    private String address;
    private Double latitude;
    private Double longitude;
    private String summary;
    private String description;
    private String coverImageUrl;
    private String openingHours;
    private String ticketPrice;
    private boolean bookingRequired;
    private String bookingNote;
    private String suggestedDuration;

    public static AttractionResponse from(Attraction attraction) {
        return AttractionResponse.builder()
                .id(attraction.getId())
                .createdAt(attraction.getCreatedAt())
                .updatedAt(attraction.getUpdatedAt())
                .slug(attraction.getSlug())
                .name(attraction.getName())
                .nameZh(attraction.getNameZh())
                .category(attraction.getCategory())
                .tags(attraction.getTags())
                .city(attraction.getCity())
                .citySlug(attraction.getCitySlug())
                .province(attraction.getProvince())
                .address(attraction.getAddress())
                .latitude(attraction.getLatitude())
                .longitude(attraction.getLongitude())
                .summary(attraction.getSummary())
                .description(attraction.getDescription())
                .coverImageUrl(attraction.getCoverImageUrl())
                .openingHours(attraction.getOpeningHours())
                .ticketPrice(attraction.getTicketPrice())
                .bookingRequired(attraction.isBookingRequired())
                .bookingNote(attraction.getBookingNote())
                .suggestedDuration(attraction.getSuggestedDuration())
                .build();
    }
}
