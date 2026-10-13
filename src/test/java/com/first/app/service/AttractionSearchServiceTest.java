package com.first.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttractionSearchServiceTest {

    private static final String NO_MATCH_TEXT = "No published attractions matched this search.";
    private static final String UNAVAILABLE_TEXT =
            "Attraction data is currently unavailable; please try again later.";

    @Mock
    private CityRepository cityRepository;

    @Mock
    private AttractionRepository attractionRepository;

    private ObjectMapper objectMapper;

    private AttractionSearchService service;

    @BeforeEach
    void setUp() {
        objectMapper = Jackson2ObjectMapperBuilder.json().build();
        service = new AttractionSearchService(cityRepository, attractionRepository, objectMapper);
    }

    @Test
    void search_resolvesCityAndMapsRowsToCompactJson() throws Exception {
        when(cityRepository.findByStatus(CityStatus.PUBLISHED)).thenReturn(List.of(hangzhou()));
        Attraction westLake = Attraction.builder()
                .slug("west-lake").name("West Lake").nameZh("西湖")
                .category(AttractionCategory.NATURE).city("杭州").citySlug("hangzhou")
                .summary("Famous lake").ratingScore(4.8).heatScore(95)
                .openingHours("All day").ticketPrice("Free")
                .suggestedDuration("3h").address("西湖区")
                .status(AttractionStatus.PUBLISHED).build();
        Attraction teaMuseum = Attraction.builder()
                .slug("tea-museum").name("Tea Museum").nameZh("茶叶博物馆")
                .category(AttractionCategory.MUSEUM).city("杭州").citySlug("hangzhou")
                .summary("A museum about tea").ratingScore(4.2).heatScore(60)
                .bookingRequired(true).status(AttractionStatus.PUBLISHED).build();
        when(attractionRepository.search(eq(AttractionStatus.PUBLISHED), eq("hangzhou"),
                eq(AttractionCategory.NATURE), eq("west"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(westLake, teaMuseum)));

        String result = service.search("HANGZHOU", "NATURE", "west", 3);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(attractionRepository).search(eq(AttractionStatus.PUBLISHED), eq("hangzhou"),
                eq(AttractionCategory.NATURE), eq("west"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(3);
        assertThat(pageable.getSort().getOrderFor("heatScore")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("heatScore").getDirection())
                .isEqualTo(Sort.Direction.DESC);

        JsonNode rows = objectMapper.readTree(result);
        assertThat(rows.isArray()).isTrue();
        assertThat(rows).hasSize(2);
        JsonNode first = rows.get(0);
        assertThat(first.path("nameZh").asText()).isEqualTo("西湖");
        assertThat(first.path("name").asText()).isEqualTo("West Lake");
        assertThat(first.path("slug").asText()).isEqualTo("west-lake");
        assertThat(first.path("city").asText()).isEqualTo("杭州");
        assertThat(first.path("category").asText()).isEqualTo("NATURE");
        assertThat(first.path("summary").asText()).isEqualTo("Famous lake");
        assertThat(first.path("ratingScore").asDouble()).isEqualTo(4.8);
        assertThat(first.path("ticketPrice").asText()).isEqualTo("Free");
        assertThat(first.path("openingHours").asText()).isEqualTo("All day");
        assertThat(first.path("suggestedDuration").asText()).isEqualTo("3h");
        assertThat(first.path("address").asText()).isEqualTo("西湖区");
        assertThat(first.has("bookingRequired")).isFalse();
        JsonNode second = rows.get(1);
        assertThat(second.path("bookingRequired").asBoolean()).isTrue();
        assertThat(second.has("ticketPrice")).isFalse();
        assertThat(second.has("openingHours")).isFalse();
        assertThat(second.has("suggestedDuration")).isFalse();
        assertThat(second.has("address")).isFalse();
        assertThat(result).doesNotContain("\n");
    }

    @Test
    void search_escapingLikeWildcardsAndDefaultLimit() {
        when(attractionRepository.search(eq(AttractionStatus.PUBLISHED), isNull(), isNull(),
                eq("50!%!_off!!"), any(Pageable.class))).thenReturn(Page.empty());

        String result = service.search(null, null, "50%_off!", null);

        assertThat(result).isEqualTo(NO_MATCH_TEXT);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(attractionRepository).search(eq(AttractionStatus.PUBLISHED), isNull(), isNull(),
                eq("50!%!_off!!"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void search_blankFilters_queryWithoutCityFilter() {
        when(attractionRepository.search(eq(AttractionStatus.PUBLISHED), isNull(), isNull(),
                isNull(), any(Pageable.class))).thenReturn(Page.empty());

        service.search("  ", "  ", "  ", 5);

        verifyNoInteractions(cityRepository);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(attractionRepository).search(eq(AttractionStatus.PUBLISHED), isNull(), isNull(),
                isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void search_unknownCity_returnsNoMatchNoticeWithoutQueryingAttractions() {
        when(cityRepository.findByStatus(CityStatus.PUBLISHED)).thenReturn(List.of(hangzhou()));

        String result = service.search("Nanjing", null, null, null);

        assertThat(result).isEqualTo(NO_MATCH_TEXT);
        verifyNoInteractions(attractionRepository);
    }

    @Test
    void search_invalidCategory_returnsInputRejectionEnvelope() throws Exception {
        String result = service.search(null, "CASTLE", null, null);

        JsonNode envelope = objectMapper.readTree(result);
        assertThat(envelope.path("error").path("kind").asText()).isEqualTo("input-rejection");
        assertThat(envelope.path("error").path("reason").asText()).contains("CASTLE");
        verifyNoInteractions(cityRepository, attractionRepository);
    }

    @Test
    void search_limitOutOfRange_returnsInputRejectionEnvelope() throws Exception {
        for (Integer badLimit : List.of(0, 11)) {
            String result = service.search(null, null, null, badLimit);

            JsonNode envelope = objectMapper.readTree(result);
            assertThat(envelope.path("error").path("kind").asText())
                    .as("limit=%s", badLimit).isEqualTo("input-rejection");
            assertThat(envelope.path("error").path("reason").asText()).contains("limit");
        }
        verifyNoInteractions(cityRepository, attractionRepository);
    }

    @Test
    void search_noMatches_returnsNoMatchNotice() {
        when(cityRepository.findByStatus(CityStatus.PUBLISHED)).thenReturn(List.of(hangzhou()));
        when(attractionRepository.search(eq(AttractionStatus.PUBLISHED), eq("hangzhou"), isNull(),
                isNull(), any(Pageable.class))).thenReturn(Page.empty());

        String result = service.search("杭州", null, null, null);

        assertThat(result).isEqualTo(NO_MATCH_TEXT);
    }

    @Test
    void search_attractionRepositoryFailure_returnsUnavailableNotice() {
        when(cityRepository.findByStatus(CityStatus.PUBLISHED)).thenReturn(List.of(hangzhou()));
        when(attractionRepository.search(any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        String result = service.search("杭州", null, null, null);

        assertThat(result).isEqualTo(UNAVAILABLE_TEXT);
    }

    @Test
    void search_cityLookupFailure_returnsUnavailableNotice() {
        when(cityRepository.findByStatus(CityStatus.PUBLISHED))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        String result = service.search("杭州", null, null, null);

        assertThat(result).isEqualTo(UNAVAILABLE_TEXT);
        verifyNoInteractions(attractionRepository);
    }

    private static City hangzhou() {
        return City.builder()
                .slug("hangzhou").name("Hangzhou").nameZh("杭州")
                .status(CityStatus.PUBLISHED).build();
    }
}
