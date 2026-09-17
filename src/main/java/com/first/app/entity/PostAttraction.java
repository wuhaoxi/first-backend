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
@Table(name = "post_attractions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_attractions_post_attraction", columnNames = {"post_id", "attraction_id"})
}, indexes = {
        @Index(name = "idx_post_attractions_attraction_id", columnList = "attraction_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PostAttraction extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "attraction_id", nullable = false)
    private Long attractionId;
}
