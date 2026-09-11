package com.first.app.repository;

import com.first.app.entity.Vote;
import com.first.app.entity.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByPostIdAndUserId(Long postId, Long userId);

    long countByPostIdAndVoteType(Long postId, VoteType voteType);

    @Query("SELECT v.postId, COUNT(v) FROM Vote v WHERE v.postId IN :postIds AND v.voteType = :voteType GROUP BY v.postId")
    List<Object[]> countByPostIdsAndVoteType(@Param("postIds") List<Long> postIds,
                                             @Param("voteType") VoteType voteType);
}
