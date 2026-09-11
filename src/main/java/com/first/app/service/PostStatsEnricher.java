package com.first.app.service;

import com.first.app.dto.PostSummary;
import com.first.app.entity.VoteType;
import com.first.app.repository.BookmarkRepository;
import com.first.app.repository.VoteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Batch-enriches {@link PostSummary} lists with live interaction statistics.
 *
 * <p>Aggregates {@code upVoteCount} / {@code bookmarkCount} for a whole page with two
 * GROUP BY queries scoped to the page's post IDs (never per item). {@code commentCount}
 * is not computed here — it comes from the denormalized {@code Post.commentCount}.</p>
 */
@Service
public class PostStatsEnricher {

    private final VoteRepository voteRepository;
    private final BookmarkRepository bookmarkRepository;

    public PostStatsEnricher(VoteRepository voteRepository, BookmarkRepository bookmarkRepository) {
        this.voteRepository = voteRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    public void enrich(List<PostSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        List<Long> postIds = summaries.stream()
                .map(PostSummary::getId)
                .toList();
        Map<Long, Integer> upVoteCounts = toCountMap(voteRepository.countByPostIdsAndVoteType(postIds, VoteType.UP));
        Map<Long, Integer> bookmarkCounts = toCountMap(bookmarkRepository.countByPostIds(postIds));
        for (PostSummary summary : summaries) {
            summary.setUpVoteCount(upVoteCounts.getOrDefault(summary.getId(), 0));
            summary.setBookmarkCount(bookmarkCounts.getOrDefault(summary.getId(), 0));
        }
    }

    private static Map<Long, Integer> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> ((Number) row[1]).intValue()));
    }
}
