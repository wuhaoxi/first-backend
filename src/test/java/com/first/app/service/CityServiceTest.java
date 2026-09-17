package com.first.app.service;

import com.first.app.dto.CityResponse;
import com.first.app.dto.CitySummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
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
class CityServiceTest {

    private static final Sort NAME_SORT = Sort.by(
            Sort.Order.asc("name"), Sort.Order.asc("id"));

    @Mock
    private CityRepository cityRepository;

    @Mock
    private AttractionRepository attractionRepository;

    @InjectMocks
    private CityService cityService;

    @Test
    void list_mapsEntitiesToSummariesInsidePageResponse() {
        City beijing = buildCity(1L, "beijing", "Beijing", "北京");
        City chengdu = buildCity(2L, "chengdu", "Chengdu", "成都");
        Page<City> page = new PageImpl<>(List.of(beijing, chengdu), Pageable.ofSize(20), 2);
        when(cityRepository.findByStatus(eq(CityStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<CitySummaryResponse> response = cityService.list(0, 20);

        assertThat(response.getContent()).hasSize(2);
        CitySummaryResponse firstItem = response.getContent().get(0);
        assertThat(firstItem.getId()).isEqualTo(1L);
        assertThat(firstItem.getSlug()).isEqualTo("beijing");
        assertThat(firstItem.getName()).isEqualTo("Beijing");
        assertThat(firstItem.getNameZh()).isEqualTo("北京");
        assertThat(firstItem.getBestSeason()).isEqualTo("Best season of Beijing");
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(2);
    }

    @Test
    void list_forwardsSortIntoRepositoryCall() {
        when(cityRepository.findByStatus(eq(CityStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(10), 0));

        cityService.list(1, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(cityRepository).findByStatus(eq(CityStatus.PUBLISHED), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).isEqualTo(NAME_SORT);
    }

    @Test
    void list_invalidPagination_throwsBadRequest() {
        assertThatThrownBy(() -> cityService.list(-1, 20))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> cityService.list(0, 0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> cityService.list(0, 101))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(cityRepository, attractionRepository);
    }

    @Test
    void getBySlug_returnsDetailWithAttractionCount() {
        City city = buildCity(7L, "beijing", "Beijing", "北京");
        city.setCoverImageUrl("https://example.com/beijing.jpg");
        city.setDescription("Capital of China with imperial landmarks.");
        city.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        city.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        when(cityRepository.findBySlugAndStatus("beijing", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city));
        when(attractionRepository.countByCitySlugAndStatus("beijing", AttractionStatus.PUBLISHED))
                .thenReturn(4L);

        CityResponse response = cityService.getBySlug("beijing");

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getSlug()).isEqualTo("beijing");
        assertThat(response.getName()).isEqualTo("Beijing");
        assertThat(response.getNameZh()).isEqualTo("北京");
        assertThat(response.getCoverImageUrl()).isEqualTo("https://example.com/beijing.jpg");
        assertThat(response.getBestSeason()).isEqualTo("Best season of Beijing");
        assertThat(response.getDescription()).isEqualTo("Capital of China with imperial landmarks.");
        assertThat(response.getAttractionCount()).isEqualTo(4L);
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(response.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
    }

    @Test
    void getBySlug_unknownSlug_throwsNotFound() {
        when(cityRepository.findBySlugAndStatus("no-such-city", CityStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cityService.getBySlug("no-such-city"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no-such-city");

        verifyNoInteractions(attractionRepository);
    }

    @Test
    void getBySlug_draftSlug_throwsNotFoundIdentically() {
        when(cityRepository.findBySlugAndStatus("secret-city", CityStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cityService.getBySlug("secret-city"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("secret-city");
    }

    private City buildCity(Long id, String slug, String name, String nameZh) {
        City city = City.builder()
                .slug(slug)
                .name(name)
                .nameZh(nameZh)
                .bestSeason("Best season of " + name)
                .status(CityStatus.PUBLISHED)
                .build();
        city.setId(id);
        return city;
    }
}
