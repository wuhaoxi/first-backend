package com.first.app.service;

import com.first.app.dto.AttractionResponse;
import com.first.app.dto.AttractionSummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.AttractionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttractionServiceTest {

    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.desc("isPopular"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort POPULAR_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Mock
    private AttractionRepository attractionRepository;

    @InjectMocks
    private AttractionService attractionService;

    @Test
    void list_mapsEntitiesToSummariesInsidePageResponse() {
        Attraction first = buildAttraction(1L, "forbidden-city", "Forbidden City");
        Attraction second = buildAttraction(2L, "great-wall", "Great Wall");
        Page<Attraction> page = new PageImpl<>(List.of(first, second), Pageable.ofSize(20), 2);
        when(attractionRepository.search(eq(AttractionStatus.PUBLISHED), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<AttractionSummaryResponse> response = attractionService.list(null, null, 0, 20);

        assertThat(response.getContent()).hasSize(2);
        AttractionSummaryResponse firstItem = response.getContent().get(0);
        assertThat(firstItem.getId()).isEqualTo(1L);
        assertThat(firstItem.getSlug()).isEqualTo("forbidden-city");
        assertThat(firstItem.getName()).isEqualTo("Forbidden City");
        assertThat(firstItem.getNameZh()).isEqualTo("故宫");
        assertThat(firstItem.getCategory()).isEqualTo(AttractionCategory.HISTORICAL_SITE);
        assertThat(firstItem.getTags()).containsExactly("unesco");
        assertThat(firstItem.getCitySlug()).isEqualTo("beijing");
        assertThat(firstItem.getSummary()).isEqualTo("Summary of Forbidden City");
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(2);
    }

    @Test
    void list_forwardsFiltersAndSortsIntoRepositoryCall() {
        when(attractionRepository.search(eq(AttractionStatus.PUBLISHED), eq("beijing"),
                eq(AttractionCategory.HISTORICAL_SITE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(10), 0));

        attractionService.list("beijing", "HISTORICAL_SITE", 1, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(attractionRepository).search(
                eq(AttractionStatus.PUBLISHED), eq("beijing"),
                eq(AttractionCategory.HISTORICAL_SITE), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).isEqualTo(LIST_SORT);
    }

    @Test
    void list_invalidCategory_throwsBadRequest() {
        assertThatThrownBy(() -> attractionService.list(null, "BOGUS", 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("BOGUS");

        verifyNoInteractions(attractionRepository);
    }

    @Test
    void list_invalidPagination_throwsBadRequest() {
        assertThatThrownBy(() -> attractionService.list(null, null, -1, 20))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> attractionService.list(null, null, 0, 0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> attractionService.list(null, null, 0, 101))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(attractionRepository);
    }

    @Test
    void popular_returnsSummariesWithinLimit() {
        when(attractionRepository.findByStatusAndIsPopularTrue(eq(AttractionStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(List.of(
                        buildAttraction(2L, "great-wall", "Great Wall"),
                        buildAttraction(1L, "forbidden-city", "Forbidden City")));

        List<AttractionSummaryResponse> response = attractionService.popular(6);

        assertThat(response).extracting(AttractionSummaryResponse::getSlug)
                .containsExactly("great-wall", "forbidden-city");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(attractionRepository)
                .findByStatusAndIsPopularTrue(eq(AttractionStatus.PUBLISHED), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(6);
        assertThat(captor.getValue().getSort()).isEqualTo(POPULAR_SORT);
    }

    @Test
    void popular_invalidLimit_throwsBadRequest() {
        assertThatThrownBy(() -> attractionService.popular(0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> attractionService.popular(13))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(attractionRepository);
    }

    @Test
    void getBySlug_returnsFullDetailResponse() {
        Attraction attraction = buildAttraction(7L, "forbidden-city", "Forbidden City");
        attraction.setProvince("Beijing");
        attraction.setAddress("4 Jingshan Front Street");
        attraction.setLatitude(39.9163);
        attraction.setLongitude(116.3972);
        attraction.setDescription("The Forbidden City served as the home of emperors.");
        attraction.setCoverImageUrl("https://example.com/forbidden-city.jpg");
        attraction.setOpeningHours("08:30-17:00 (closed Mondays)");
        attraction.setTicketPrice("¥60");
        attraction.setBookingRequired(true);
        attraction.setBookingNote("Book with passport 7 days ahead");
        attraction.setSuggestedDuration("2-3 hours");
        attraction.setPopular(true);
        attraction.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        attraction.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        when(attractionRepository.findBySlugAndStatus("forbidden-city", AttractionStatus.PUBLISHED))
                .thenReturn(Optional.of(attraction));

        AttractionResponse response = attractionService.getBySlug("forbidden-city");

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getSlug()).isEqualTo("forbidden-city");
        assertThat(response.getNameZh()).isEqualTo("故宫");
        assertThat(response.getCategory()).isEqualTo(AttractionCategory.HISTORICAL_SITE);
        assertThat(response.getTags()).containsExactly("unesco");
        assertThat(response.getCity()).isEqualTo("Beijing");
        assertThat(response.getCitySlug()).isEqualTo("beijing");
        assertThat(response.getProvince()).isEqualTo("Beijing");
        assertThat(response.getAddress()).isEqualTo("4 Jingshan Front Street");
        assertThat(response.getLatitude()).isEqualTo(39.9163);
        assertThat(response.getLongitude()).isEqualTo(116.3972);
        assertThat(response.getSummary()).isEqualTo("Summary of Forbidden City");
        assertThat(response.getDescription()).startsWith("The Forbidden City");
        assertThat(response.getCoverImageUrl()).isEqualTo("https://example.com/forbidden-city.jpg");
        assertThat(response.getOpeningHours()).isEqualTo("08:30-17:00 (closed Mondays)");
        assertThat(response.getTicketPrice()).isEqualTo("¥60");
        assertThat(response.isBookingRequired()).isTrue();
        assertThat(response.getBookingNote()).isEqualTo("Book with passport 7 days ahead");
        assertThat(response.getSuggestedDuration()).isEqualTo("2-3 hours");
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(response.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
    }

    @Test
    void getBySlug_unknownSlug_throwsNotFound() {
        when(attractionRepository.findBySlugAndStatus("no-such-place", AttractionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attractionService.getBySlug("no-such-place"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no-such-place");
    }

    @Test
    void getBySlug_draftSlug_throwsNotFoundIdentically() {
        when(attractionRepository.findBySlugAndStatus("secret-spot", AttractionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attractionService.getBySlug("secret-spot"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("secret-spot");
    }

    private Attraction buildAttraction(Long id, String slug, String name) {
        Attraction attraction = Attraction.builder()
                .slug(slug)
                .name(name)
                .nameZh(name.equals("Forbidden City") ? "故宫" : name + "中文")
                .category(AttractionCategory.HISTORICAL_SITE)
                .tags(List.of("unesco"))
                .city("Beijing")
                .citySlug("beijing")
                .summary("Summary of " + name)
                .description("Description of " + name)
                .status(AttractionStatus.PUBLISHED)
                .build();
        attraction.setId(id);
        return attraction;
    }
}
