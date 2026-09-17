package com.first.app.service;

import com.first.app.dto.LinkedAttractionResponse;
import com.first.app.dto.PostResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.Post;
import com.first.app.entity.PostAttraction;
import com.first.app.entity.PostStatus;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.PostAttractionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostAttractionEnricherTest {

    @Mock
    private PostAttractionRepository postAttractionRepository;

    @Mock
    private AttractionRepository attractionRepository;

    @InjectMocks
    private PostAttractionEnricher postAttractionEnricher;

    @Test
    void enrich_mapsLinkedPublishedAttractionsInJunctionOrder() {
        PostResponse response = buildResponse(1L);
        when(postAttractionRepository.findByPostIdOrderByIdAsc(1L))
                .thenReturn(List.of(link(1L, 7L), link(1L, 2L)));
        // Repository result order is arbitrary (IN clause) — junction order must win.
        when(attractionRepository.findByIdInAndStatus(any(), eq(AttractionStatus.PUBLISHED)))
                .thenReturn(List.of(buildAttraction(2L), buildAttraction(7L)));

        postAttractionEnricher.enrich(response);

        assertThat(response.getAttractions()).extracting(LinkedAttractionResponse::getId)
                .containsExactly(7L, 2L);
        assertThat(response.getAttractions()).extracting(LinkedAttractionResponse::getSlug)
                .containsExactly("attraction-7", "attraction-2");
        assertThat(response.getAttractions()).extracting(LinkedAttractionResponse::getNameZh)
                .containsExactly("景点7", "景点2");

        // Exactly two repository queries: one junction lookup + one batched attraction fetch.
        verify(postAttractionRepository).findByPostIdOrderByIdAsc(1L);
        verify(attractionRepository).findByIdInAndStatus(any(), eq(AttractionStatus.PUBLISHED));
    }

    @Test
    void enrich_filtersDraftOrVanishedAttractions() {
        PostResponse response = buildResponse(1L);
        when(postAttractionRepository.findByPostIdOrderByIdAsc(1L))
                .thenReturn(List.of(link(1L, 7L), link(1L, 3L)));
        when(attractionRepository.findByIdInAndStatus(any(), eq(AttractionStatus.PUBLISHED)))
                .thenReturn(List.of(buildAttraction(7L)));

        postAttractionEnricher.enrich(response);

        assertThat(response.getAttractions()).extracting(LinkedAttractionResponse::getId)
                .containsExactly(7L);
    }

    @Test
    void enrich_noLinks_setsEmptyListAndSkipsAttractionQuery() {
        PostResponse response = buildResponse(1L);
        when(postAttractionRepository.findByPostIdOrderByIdAsc(1L)).thenReturn(List.of());

        postAttractionEnricher.enrich(response);

        assertThat(response.getAttractions()).isEmpty();
        verify(postAttractionRepository).findByPostIdOrderByIdAsc(1L);
        verifyNoInteractions(attractionRepository);
    }

    @Test
    void enrich_nullId_isNoOp() {
        PostResponse response = buildResponse(null);

        postAttractionEnricher.enrich(response);

        assertThat(response.getAttractions()).isEmpty();
        verifyNoInteractions(postAttractionRepository, attractionRepository);
    }

    private static PostResponse buildResponse(Long id) {
        Post post = Post.builder()
                .id(id)
                .title("Post")
                .content("content")
                .status(PostStatus.PUBLISHED)
                .authorId(1L)
                .build();
        return PostResponse.from(post);
    }

    private static PostAttraction link(Long postId, Long attractionId) {
        return PostAttraction.builder()
                .postId(postId)
                .attractionId(attractionId)
                .build();
    }

    private static Attraction buildAttraction(Long id) {
        Attraction attraction = Attraction.builder()
                .slug("attraction-" + id)
                .name("Attraction " + id)
                .nameZh("景点" + id)
                .category(AttractionCategory.MUSEUM)
                .city("Beijing")
                .citySlug("beijing")
                .summary("Summary of attraction " + id)
                .description("Description of attraction " + id)
                .status(AttractionStatus.PUBLISHED)
                .build();
        attraction.setId(id);
        return attraction;
    }
}
