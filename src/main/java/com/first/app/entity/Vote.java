package com.first.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "votes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_votes_post_user", columnNames = {"post_id", "user_id"})
}, indexes = {
        @Index(name = "idx_votes_post_id", columnList = "post_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Vote extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false, length = 4)
    private VoteType voteType;
}
