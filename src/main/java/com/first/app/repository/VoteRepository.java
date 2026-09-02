package com.first.app.repository;

import com.first.app.entity.Vote;
import com.first.app.entity.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByPostIdAndUserId(Long postId, Long userId);

    long countByPostIdAndVoteType(Long postId, VoteType voteType);
}
