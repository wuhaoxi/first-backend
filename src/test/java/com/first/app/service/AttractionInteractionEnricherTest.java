package com.first.app.service;

import com.first.app.dto.AttractionResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionFavorite;
import com.first.app.entity.AttractionStatus;
import com.first.app.repository.AttractionCommentRepository;
import com.first.app.repository.AttractionFavoriteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttractionInteractionEnricherTest {

    @Mock
    private AttractionCommentRepository attractionCommentRepository;

    @Mock
    private AttractionFavoriteRepository attractionFavoriteRepository;

    @InjectMocks
    private AttractionInteractionEnricher attractionInteractionEnricher;

    private static final Long ATTRACTION_ID = 7L;
    private static final Long USER_ID = 1L;

    @Test
    void enrich_authenticatedUser_setsLiveCountsAndFavoritedTrue() {
        AttractionResponse response = buildResponse();
        when(attractionCommentRepository.countByAttractionIdAndDeletedFalse(ATTRACTION_ID)).thenReturn(3L);
        when(attractionFavoriteRepository.countByAttractionId(ATTRACTION_ID)).thenReturn(2L);
        when(attractionFavoriteRepository.findByAttractionIdAndUserId(ATTRACTION_ID, USER_ID))
                .thenReturn(Optional.of(AttractionFavorite.builder()
                        .attractionId(ATTRACTION_ID).userId(USER_ID).build()));

        attractionInteractionEnricher.enrich(response, USER_ID);

        assertThat(response.getCommentCount()).isEqualTo(3);
        // Stored curated base (6200) + live favorites (2)
        assertThat(response.getFavoriteCount()).isEqualTo(6202);
        assertThat(response.getFavorited()).isTrue();
    }

    @Test
    void enrich_authenticatedUser_setsFavoritedFalseWhenAbsent() {
        AttractionResponse response = buildResponse();
        when(attractionCommentRepository.countByAttractionIdAndDeletedFalse(ATTRACTION_ID)).thenReturn(0L);
        when(attractionFavoriteRepository.countByAttractionId(ATTRACTION_ID)).thenReturn(0L);
        when(attractionFavoriteRepository.findByAttractionIdAndUserId(ATTRACTION_ID, USER_ID))
                .thenReturn(Optional.empty());

        attractionInteractionEnricher.enrich(response, USER_ID);

        assertThat(response.getFavorited()).isFalse();
        assertThat(response.getCommentCount()).isZero();
        assertThat(response.getFavoriteCount()).isEqualTo(6200);
    }

    @Test
    void enrich_anonymous_leavesFavoritedNullAndSkipsUserLookup() {
        AttractionResponse response = buildResponse();
        when(attractionCommentRepository.countByAttractionIdAndDeletedFalse(ATTRACTION_ID)).thenReturn(4L);
        when(attractionFavoriteRepository.countByAttractionId(ATTRACTION_ID)).thenReturn(1L);

        attractionInteractionEnricher.enrich(response, null);

        assertThat(response.getFavorited()).isNull();
        assertThat(response.getCommentCount()).isEqualTo(4);
        assertThat(response.getFavoriteCount()).isEqualTo(6201);
        verify(attractionFavoriteRepository, never()).findByAttractionIdAndUserId(any(), any());
    }

    @Test
    void enrich_nullId_isNoOp() {
        AttractionResponse response = buildResponse();
        response.setId(null);

        attractionInteractionEnricher.enrich(response, USER_ID);

        assertThat(response.getCommentCount()).isZero();
        assertThat(response.getFavorited()).isNull();
        assertThat(response.getFavoriteCount()).isEqualTo(6200);
        verifyNoInteractions(attractionCommentRepository, attractionFavoriteRepository);
    }

    private AttractionResponse buildResponse() {
        Attraction attraction = Attraction.builder()
                .slug("forbidden-city")
                .name("Forbidden City")
                .nameZh("故宫")
                .category(AttractionCategory.HISTORICAL_SITE)
                .city("Beijing")
                .citySlug("beijing")
                .summary("Summary of Forbidden City")
                .description("Description of Forbidden City")
                .status(AttractionStatus.PUBLISHED)
                .favoriteCount(6200)
                .build();
        attraction.setId(ATTRACTION_ID);
        return AttractionResponse.from(attraction);
    }
}
