package com.first.app.entity;

import com.first.app.converter.StringListConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attractions", indexes = {
    @Index(name = "idx_attractions_status_city", columnList = "status, city_slug")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_attractions_slug", columnNames = "slug")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Attraction extends BaseEntity {

    @NotBlank(message = "slug must not be blank")
    @Size(max = 120, message = "slug must not exceed 120 characters")
    @Column(nullable = false, length = 120)
    private String slug;

    @NotBlank(message = "name must not be blank")
    @Size(max = 150, message = "name must not exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank(message = "nameZh must not be blank")
    @Size(max = 150, message = "nameZh must not exceed 150 characters")
    @Column(name = "name_zh", nullable = false, length = 150)
    private String nameZh;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttractionCategory category;

    @Convert(converter = StringListConverter.class)
    @Column(length = 500)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "city_slug", nullable = false, length = 80)
    private String citySlug;

    @Column(length = 80)
    private String province;

    @Column(length = 255)
    private String address;

    private Double latitude;

    private Double longitude;

    @NotBlank(message = "summary must not be blank")
    @Size(max = 300, message = "summary must not exceed 300 characters")
    @Column(nullable = false, length = 300)
    private String summary;

    @NotBlank(message = "description must not be blank")
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "opening_hours", length = 200)
    private String openingHours;

    @Column(name = "ticket_price", length = 200)
    private String ticketPrice;

    @Column(name = "booking_required", nullable = false)
    @Builder.Default
    private boolean bookingRequired = false;

    @Column(name = "booking_note", length = 300)
    private String bookingNote;

    @Column(name = "suggested_duration", length = 100)
    private String suggestedDuration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttractionStatus status = AttractionStatus.DRAFT;

    @Column(name = "is_popular", nullable = false)
    @Builder.Default
    private boolean isPopular = false;
}
