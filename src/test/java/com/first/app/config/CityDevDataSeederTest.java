package com.first.app.config;

import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.repository.CityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityDevDataSeederTest {

    @Mock
    private CityRepository cityRepository;

    @InjectMocks
    private CityDevDataSeeder seeder;

    @Test
    void run_whenTableEmpty_insertsSixCuratedCities() throws Exception {
        when(cityRepository.count()).thenReturn(0L);

        seeder.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<City>> captor = ArgumentCaptor.forClass(List.class);
        verify(cityRepository).saveAll(captor.capture());
        List<City> saved = captor.getValue();

        assertThat(saved).hasSize(6);
        assertThat(saved).allMatch(c -> c.getStatus() == CityStatus.PUBLISHED);

        Set<String> slugs = saved.stream().map(City::getSlug).collect(Collectors.toSet());
        assertThat(slugs).containsExactlyInAnyOrder(
                "beijing", "xian", "shanghai", "chengdu", "hangzhou", "guilin");

        assertThat(saved).allSatisfy(c -> {
            assertThat(c.getName()).isNotBlank();
            assertThat(c.getNameZh()).isNotBlank();
            assertThat(c.getDescription()).isNotBlank();
            assertThat(c.getBestSeason()).isNotBlank();
        });
    }

    @Test
    void run_whenTableEmpty_seedsMainlandCoordinatesForAllSixCities() throws Exception {
        when(cityRepository.count()).thenReturn(0L);

        seeder.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<City>> captor = ArgumentCaptor.forClass(List.class);
        verify(cityRepository).saveAll(captor.capture());
        List<City> saved = captor.getValue();

        // Spec bounds: every seeded city sits inside mainland China.
        assertThat(saved).allSatisfy(c -> {
            assertThat(c.getLatitude()).as("latitude of %s", c.getSlug())
                    .isNotNull().isBetween(18.0, 45.0);
            assertThat(c.getLongitude()).as("longitude of %s", c.getSlug())
                    .isNotNull().isBetween(73.0, 135.0);
        });

        // Exact values shared with the 4.5 backfill SQL so the two cannot drift.
        Map<String, double[]> expected = Map.of(
                "beijing", new double[] {39.9042, 116.4074},
                "xian", new double[] {34.3416, 108.9398},
                "shanghai", new double[] {31.2304, 121.4737},
                "chengdu", new double[] {30.5728, 104.0668},
                "hangzhou", new double[] {30.2741, 120.1551},
                "guilin", new double[] {25.2736, 110.2900});
        assertThat(saved).allSatisfy(c -> {
            double[] coordinates = expected.get(c.getSlug());
            assertThat(coordinates).as("expectation for %s", c.getSlug()).isNotNull();
            assertThat(c.getLatitude()).as("latitude of %s", c.getSlug())
                    .isEqualTo(coordinates[0]);
            assertThat(c.getLongitude()).as("longitude of %s", c.getSlug())
                    .isEqualTo(coordinates[1]);
        });
    }

    @Test
    void run_whenTableNotEmpty_skipsSeeding() throws Exception {
        when(cityRepository.count()).thenReturn(6L);

        seeder.run();

        verify(cityRepository, never()).saveAll(any());
    }
}
