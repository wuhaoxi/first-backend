package com.first.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "attraction_favorites", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attraction_favorites_attraction_user", columnNames = {"attraction_id", "user_id"})
}, indexes = {
        @Index(name = "idx_attraction_favorites_user_id", columnList = "user_id"),
        @Index(name = "idx_attraction_favorites_attraction_id", columnList = "attraction_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AttractionFavorite extends BaseEntity {

    @Column(name = "attraction_id", nullable = false)
    private Long attractionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
