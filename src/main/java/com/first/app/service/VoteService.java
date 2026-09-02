package com.first.app.service;

import com.first.app.dto.VoteStatsResponse;
import com.first.app.entity.Vote;
import com.first.app.entity.VoteType;
import com.first.app.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteService {

    private final VoteRepository voteRepository;
    private final PostService postService;

    @Transactional
    public VoteStatsResponse vote(Long postId, VoteType voteType, Long userId) {
        postService.findByIdPublic(postId);

        Vote existing = voteRepository.findByPostIdAndUserId(postId, userId).orElse(null);

        if (existing == null) {
            voteRepository.save(Vote.builder()
                    .postId(postId)
                    .userId(userId)
                    .voteType(voteType)
                    .build());
        } else if (existing.getVoteType() == voteType) {
            // Cancel = hard delete — keeps the (post_id, user_id) unique constraint free for re-votes
            voteRepository.delete(existing);
        } else {
            existing.setVoteType(voteType);
            voteRepository.save(existing);
        }

        return getVoteStats(postId, userId);
    }

    public VoteStatsResponse getVoteStats(Long postId, Long userId) {
        postService.findByIdPublic(postId);

        long upCount = voteRepository.countByPostIdAndVoteType(postId, VoteType.UP);
        long downCount = voteRepository.countByPostIdAndVoteType(postId, VoteType.DOWN);
        VoteType userVote = null;
        if (userId != null) {
            userVote = voteRepository.findByPostIdAndUserId(postId, userId)
                    .map(Vote::getVoteType)
                    .orElse(null);
        }

        return VoteStatsResponse.builder()
                .upCount(upCount)
                .downCount(downCount)
                .userVote(userVote)
                .build();
    }
}
