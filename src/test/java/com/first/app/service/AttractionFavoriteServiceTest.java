package com.first.app.service;

import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionFavorite;
import com.first.app.entity.AttractionStatus;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.AttractionFavoriteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttractionFavoriteServiceTest {

    @Mock
    private AttractionFavoriteRepository attractionFavoriteRepository;

    @Mock
    private AttractionService attractionService;

    @InjectMocks
    private AttractionFavoriteService attractionFavoriteService;

    private static final Long ATTRACTION_ID = 7L;
    private static final Long USER_ID = 1L;

    private Attraction buildAttraction() {
        Attraction attraction = Attraction.builder()
                .slug("forbidden-city").name("Forbidden City")
                .status(AttractionStatus.PUBLISHED)
                .build();
        attraction.setId(ATTRACTION_ID);
        return attraction;
    }

    @Test
    void toggle_whenNotFavorited_savesAndReturnsTrue() {
        when(attractionService.findByIdPublic(ATTRACTION_ID)).thenReturn(buildAttraction());
        when(attractionFavoriteRepository.findByAttractionIdAndUserId(ATTRACTION_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(attractionFavoriteRepository.save(any(AttractionFavorite.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        boolean favorited = attractionFavoriteService.toggle(ATTRACTION_ID, USER_ID);

        assertThat(favorited).isTrue();
        ArgumentCaptor<AttractionFavorite> captor = ArgumentCaptor.forClass(AttractionFavorite.class);
        verify(attractionFavoriteRepository).save(captor.capture());
        assertThat(captor.getValue().getAttractionId()).isEqualTo(ATTRACTION_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        verify(attractionFavoriteRepository, never()).delete(any(AttractionFavorite.class));
    }

    @Test
    void toggle_whenAlreadyFavorited_hardDeletesAndReturnsFalse() {
        AttractionFavorite existing = AttractionFavorite.builder()
                .attractionId(ATTRACTION_ID).userId(USER_ID).build();
        when(attractionService.findByIdPublic(ATTRACTION_ID)).thenReturn(buildAttraction());
        when(attractionFavoriteRepository.findByAttractionIdAndUserId(ATTRACTION_ID, USER_ID))
                .thenReturn(Optional.of(existing));

        boolean favorited = attractionFavoriteService.toggle(ATTRACTION_ID, USER_ID);

        assertThat(favorited).isFalse();
        verify(attractionFavoriteRepository).delete(existing);
        verify(attractionFavoriteRepository, never()).save(any(AttractionFavorite.class));
    }

    @Test
    void toggle_unknownOrDraftAttraction_throws404WithoutTouchingFavorites() {
        when(attractionService.findByIdPublic(ATTRACTION_ID))
                .thenThrow(new ResourceNotFoundException("Attraction not found with id: " + ATTRACTION_ID));

        assertThatThrownBy(() -> attractionFavoriteService.toggle(ATTRACTION_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(attractionFavoriteRepository);
    }
}
