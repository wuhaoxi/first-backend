package com.first.app.service;

import com.first.app.dto.PostSummary;
import com.first.app.entity.VoteType;
import com.first.app.repository.BookmarkRepository;
import com.first.app.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostStatsEnricherTest {

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @InjectMocks
    private PostStatsEnricher postStatsEnricher;

    @Test
    void enrich_setsCountsFromBatchQueries() {
        PostSummary summary1 = buildSummary(1L);
        PostSummary summary2 = buildSummary(2L);
        PostSummary summary3 = buildSummary(3L);
        List<PostSummary> summaries = List.of(summary1, summary2, summary3);

        when(voteRepository.countByPostIdsAndVoteType(List.of(1L, 2L, 3L), VoteType.UP))
                .thenReturn(rows(row(1L, 5L), row(2L, 2L)));
        when(bookmarkRepository.countByPostIds(List.of(1L, 2L, 3L)))
                .thenReturn(rows(row(1L, 2L)));

        postStatsEnricher.enrich(summaries);

        assertThat(summary1.getUpVoteCount()).isEqualTo(5);
        assertThat(summary1.getBookmarkCount()).isEqualTo(2);
        assertThat(summary2.getUpVoteCount()).isEqualTo(2);
        assertThat(summary2.getBookmarkCount()).isZero();
        assertThat(summary3.getUpVoteCount()).isZero();
        assertThat(summary3.getBookmarkCount()).isZero();

        verify(voteRepository).countByPostIdsAndVoteType(List.of(1L, 2L, 3L), VoteType.UP);
        verify(bookmarkRepository).countByPostIds(List.of(1L, 2L, 3L));
    }

    @Test
    void enrich_emptyList_skipsBothQueries() {
        postStatsEnricher.enrich(List.of());

        verifyNoInteractions(voteRepository, bookmarkRepository);
    }

    @Test
    void enrich_nullList_skipsBothQueries() {
        postStatsEnricher.enrich(null);

        verifyNoInteractions(voteRepository, bookmarkRepository);
    }

    private static PostSummary buildSummary(Long id) {
        return PostSummary.builder()
                .id(id)
                .title("Post " + id)
                .build();
    }

    private static Object[] row(Long postId, long count) {
        return new Object[]{postId, count};
    }

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }
}
