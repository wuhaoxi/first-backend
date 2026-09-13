package com.first.app.config;

import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionStatus;
import com.first.app.repository.AttractionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttractionDevDataSeederTest {

    private static final List<String> SHOWCASE_SLUGS = List.of(
            "mutianyu-great-wall", "forbidden-city", "terracotta-army",
            "the-bund", "chengdu-panda-base", "west-lake");

    @Mock
    private AttractionRepository attractionRepository;

    @InjectMocks
    private AttractionDevDataSeeder seeder;

    @Test
    void run_whenTableEmpty_insertsCuratedCatalog() throws Exception {
        when(attractionRepository.count()).thenReturn(0L);

        seeder.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Attraction>> captor = ArgumentCaptor.forClass(List.class);
        verify(attractionRepository).saveAll(captor.capture());
        List<Attraction> saved = captor.getValue();

        assertThat(saved).hasSize(24);
        assertThat(saved).allMatch(a -> a.getStatus() == AttractionStatus.PUBLISHED);
        assertThat(saved).filteredOn(Attraction::isPopular).hasSize(8);

        Set<String> citySlugs = saved.stream().map(Attraction::getCitySlug).collect(Collectors.toSet());
        assertThat(citySlugs).containsExactlyInAnyOrder(
                "beijing", "xian", "shanghai", "chengdu", "hangzhou", "guilin");
        for (String citySlug : citySlugs) {
            assertThat(saved).filteredOn(a -> a.getCitySlug().equals(citySlug)).hasSize(4);
        }

        assertThat(saved).allSatisfy(a -> {
            assertThat(a.getNameZh()).isNotBlank();
            assertThat(a.getTags()).isNotEmpty();
            assertThat(a.getSummary()).isNotBlank();
            assertThat(a.getDescription()).isNotBlank();
            assertThat(a.getOpeningHours()).isNotBlank();
            assertThat(a.getTicketPrice()).isNotBlank();
            assertThat(a.getSuggestedDuration()).isNotBlank();
            if (a.isBookingRequired()) {
                assertThat(a.getBookingNote()).isNotBlank();
            }
        });

        // Homepage showcases MUST be the final six entries: ids follow insertion order, and the
        // popular feed takes the top 6 by id DESC — only then do the right six reach the homepage.
        assertThat(saved.subList(18, 24)).extracting(Attraction::getSlug)
                .containsExactlyElementsOf(SHOWCASE_SLUGS);
        assertThat(saved.subList(18, 24)).allMatch(Attraction::isPopular);
        List<Attraction> populars = saved.stream().filter(Attraction::isPopular).toList();
        assertThat(populars.subList(populars.size() - 6, populars.size()))
                .extracting(Attraction::getSlug).containsExactlyElementsOf(SHOWCASE_SLUGS);
    }

    @Test
    void run_whenTableNotEmpty_skipsSeeding() throws Exception {
        when(attractionRepository.count()).thenReturn(24L);

        seeder.run();

        verify(attractionRepository, never()).saveAll(any());
    }
}
