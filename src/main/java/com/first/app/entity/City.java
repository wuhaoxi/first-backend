package com.first.app.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "cities", uniqueConstraints = {
    @UniqueConstraint(name = "uk_cities_slug", columnNames = "slug")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class City extends BaseEntity {

    @NotBlank(message = "slug must not be blank")
    @Size(max = 80, message = "slug must not exceed 80 characters")
    @Column(nullable = false, length = 80)
    private String slug;

    @NotBlank(message = "name must not be blank")
    @Size(max = 150, message = "name must not exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank(message = "nameZh must not be blank")
    @Size(max = 150, message = "nameZh must not exceed 150 characters")
    @Column(name = "name_zh", nullable = false, length = 150)
    private String nameZh;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "best_season", length = 100)
    private String bestSeason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CityStatus status = CityStatus.DRAFT;
}
