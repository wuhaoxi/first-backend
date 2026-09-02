package com.first.app.service;

import com.first.app.dto.VoteStatsResponse;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.entity.Vote;
import com.first.app.entity.VoteType;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private PostService postService;

    @InjectMocks
    private VoteService voteService;

    private static final Long POST_ID = 1L;
    private static final Long USER_ID = 1L;

    private Post buildPublishedPost() {
        return Post.builder()
                .id(POST_ID).title("Test Post").content("# Hello")
                .status(PostStatus.PUBLISHED).authorId(1L)
                .build();
    }

    @Test
    void vote_shouldCreateWhenNoExistingVote() {
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPublishedPost());
        // First call (initial lookup): none — Second call (stats refresh): the just-created vote
        when(voteRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.empty(), Optional.of(Vote.builder()
                        .postId(POST_ID).userId(USER_ID).voteType(VoteType.UP).build()));
        when(voteRepository.save(any(Vote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.UP)).thenReturn(1L);
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.DOWN)).thenReturn(0L);

        VoteStatsResponse stats = voteService.vote(POST_ID, VoteType.UP, USER_ID);

        assertThat(stats.getUpCount()).isEqualTo(1);
        assertThat(stats.getDownCount()).isZero();
        assertThat(stats.getUserVote()).isEqualTo(VoteType.UP);
        verify(voteRepository).save(any(Vote.class));
    }

    @Test
    void vote_shouldCancelWhenSameType() {
        Vote existing = Vote.builder().id(9L).postId(POST_ID).userId(USER_ID)
                .voteType(VoteType.UP).build();
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPublishedPost());
        // First call (initial lookup): the vote — Second call (stats refresh): deleted, so none
        when(voteRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(existing), Optional.empty());
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.UP)).thenReturn(0L);
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.DOWN)).thenReturn(0L);

        VoteStatsResponse stats = voteService.vote(POST_ID, VoteType.UP, USER_ID);

        verify(voteRepository).delete(existing);
        verify(voteRepository, never()).save(any(Vote.class));
        assertThat(stats.getUserVote()).isNull();
        assertThat(stats.getUpCount()).isZero();
    }

    @Test
    void vote_shouldSwitchWhenDifferentType() {
        Vote existing = Vote.builder().id(9L).postId(POST_ID).userId(USER_ID)
                .voteType(VoteType.UP).build();
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPublishedPost());
        when(voteRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(voteRepository.save(any(Vote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.UP)).thenReturn(0L);
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.DOWN)).thenReturn(1L);

        VoteStatsResponse stats = voteService.vote(POST_ID, VoteType.DOWN, USER_ID);

        assertThat(existing.getVoteType()).isEqualTo(VoteType.DOWN);
        verify(voteRepository).save(existing);
        assertThat(stats.getUserVote()).isEqualTo(VoteType.DOWN);
        assertThat(stats.getDownCount()).isEqualTo(1);
    }

    @Test
    void vote_shouldThrow404WhenPostNotPublished() {
        when(postService.findByIdPublic(POST_ID))
                .thenThrow(new ResourceNotFoundException("Post not found with id: " + POST_ID));

        assertThatThrownBy(() -> voteService.vote(POST_ID, VoteType.UP, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getVoteStats_shouldReturnNullUserVoteWhenAnonymous() {
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPublishedPost());
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.UP)).thenReturn(3L);
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.DOWN)).thenReturn(1L);

        VoteStatsResponse stats = voteService.getVoteStats(POST_ID, null);

        assertThat(stats.getUpCount()).isEqualTo(3);
        assertThat(stats.getDownCount()).isEqualTo(1);
        assertThat(stats.getUserVote()).isNull();
    }

    @Test
    void getVoteStats_shouldReturnUserVoteWhenAuthenticated() {
        Vote existing = Vote.builder().id(9L).postId(POST_ID).userId(USER_ID)
                .voteType(VoteType.DOWN).build();
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPublishedPost());
        when(voteRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.UP)).thenReturn(2L);
        when(voteRepository.countByPostIdAndVoteType(POST_ID, VoteType.DOWN)).thenReturn(5L);

        VoteStatsResponse stats = voteService.getVoteStats(POST_ID, USER_ID);

        assertThat(stats.getUserVote()).isEqualTo(VoteType.DOWN);
        assertThat(stats.getUpCount()).isEqualTo(2);
        assertThat(stats.getDownCount()).isEqualTo(5);
    }
}
