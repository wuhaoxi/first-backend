package com.first.app.controller;

import com.first.app.dto.AttractionResponse;
import com.first.app.dto.AttractionSummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.entity.AttractionCategory;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.AttractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttractionController.class)
@AutoConfigureMockMvc(addFilters = false)
class AttractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttractionService attractionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void list_returns200WithPageEnvelope() throws Exception {
        AttractionSummaryResponse item = AttractionSummaryResponse.builder()
                .id(1L)
                .slug("forbidden-city")
                .name("Forbidden City")
                .nameZh("故宫")
                .category(AttractionCategory.HISTORICAL_SITE)
                .tags(List.of("unesco", "must-see"))
                .city("Beijing")
                .citySlug("beijing")
                .summary("Imperial palace at the heart of Beijing")
                .coverImageUrl("https://example.com/fc.jpg")
                .bookingRequired(true)
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
        when(attractionService.list(null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/attractions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("forbidden-city"))
                .andExpect(jsonPath("$.content[0].nameZh").value("故宫"))
                .andExpect(jsonPath("$.content[0].category").value("HISTORICAL_SITE"))
                .andExpect(jsonPath("$.content[0].tags[0]").value("unesco"))
                .andExpect(jsonPath("$.content[0].bookingRequired").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void list_forwardsFilterAndPaginationParameters() throws Exception {
        when(attractionService.list("beijing", "TEMPLE", 1, 10))
                .thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));

        mockMvc.perform(get("/api/attractions?city=beijing&category=TEMPLE&page=1&size=10"))
                .andExpect(status().isOk());

        verify(attractionService).list("beijing", "TEMPLE", 1, 10);
    }

    @Test
    void list_invalidParameters_return400ErrorBody() throws Exception {
        when(attractionService.list(null, null, -1, 20))
                .thenThrow(new InvalidRequestException("page must not be negative"));
        mockMvc.perform(get("/api/attractions?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page must not be negative"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());

        when(attractionService.list(null, null, 0, 0))
                .thenThrow(new InvalidRequestException("size must be between 1 and 100"));
        mockMvc.perform(get("/api/attractions?size=0"))
                .andExpect(status().isBadRequest());

        when(attractionService.list(null, null, 0, 101))
                .thenThrow(new InvalidRequestException("size must be between 1 and 100"));
        mockMvc.perform(get("/api/attractions?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        when(attractionService.list(null, "BOGUS", 0, 20))
                .thenThrow(new InvalidRequestException("Invalid category: BOGUS"));
        mockMvc.perform(get("/api/attractions?category=BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("BOGUS")));
    }

    @Test
    void popular_returns200PlainArray() throws Exception {
        AttractionSummaryResponse item = AttractionSummaryResponse.builder()
                .id(2L)
                .slug("great-wall")
                .name("Great Wall")
                .nameZh("长城")
                .category(AttractionCategory.HISTORICAL_SITE)
                .city("Beijing")
                .citySlug("beijing")
                .summary("Ancient fortification")
                .build();
        when(attractionService.popular(6)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/attractions/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("great-wall"));
    }

    @Test
    void popular_explicitLimit_isForwarded() throws Exception {
        when(attractionService.popular(3)).thenReturn(List.of());

        mockMvc.perform(get("/api/attractions/popular?limit=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(attractionService).popular(3);
    }

    @Test
    void popular_invalidLimit_returns400ErrorBody() throws Exception {
        when(attractionService.popular(0))
                .thenThrow(new InvalidRequestException("limit must be between 1 and 12"));
        mockMvc.perform(get("/api/attractions/popular?limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        when(attractionService.popular(13))
                .thenThrow(new InvalidRequestException("limit must be between 1 and 12"));
        mockMvc.perform(get("/api/attractions/popular?limit=13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be between 1 and 12"));
    }

    @Test
    void detail_returns200WithPracticalFields() throws Exception {
        AttractionResponse detail = AttractionResponse.builder()
                .id(1L)
                .slug("forbidden-city")
                .name("Forbidden City")
                .nameZh("故宫")
                .category(AttractionCategory.HISTORICAL_SITE)
                .tags(List.of("unesco"))
                .city("Beijing")
                .citySlug("beijing")
                .province("Beijing")
                .address("4 Jingshan Front Street")
                .latitude(39.9163)
                .longitude(116.3972)
                .summary("Imperial palace at the heart of Beijing")
                .description("The Forbidden City served as the home of emperors.")
                .coverImageUrl("https://example.com/fc.jpg")
                .openingHours("08:30-17:00 (closed Mondays)")
                .ticketPrice("¥60")
                .bookingRequired(true)
                .bookingNote("Book with passport 7 days ahead")
                .suggestedDuration("2-3 hours")
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 10, 0))
                .build();
        when(attractionService.getBySlug("forbidden-city")).thenReturn(detail);

        mockMvc.perform(get("/api/attractions/forbidden-city"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameZh").value("故宫"))
                .andExpect(jsonPath("$.category").value("HISTORICAL_SITE"))
                .andExpect(jsonPath("$.openingHours").value("08:30-17:00 (closed Mondays)"))
                .andExpect(jsonPath("$.ticketPrice").value("¥60"))
                .andExpect(jsonPath("$.bookingRequired").value(true))
                .andExpect(jsonPath("$.bookingNote").value("Book with passport 7 days ahead"))
                .andExpect(jsonPath("$.suggestedDuration").value("2-3 hours"))
                .andExpect(jsonPath("$.latitude").value(39.9163))
                .andExpect(jsonPath("$.description").value("The Forbidden City served as the home of emperors."));
    }

    @Test
    void detail_unknownSlug_returns404ErrorBody() throws Exception {
        when(attractionService.getBySlug("no-such-place"))
                .thenThrow(new ResourceNotFoundException("Attraction not found: no-such-place"));

        mockMvc.perform(get("/api/attractions/no-such-place"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Attraction not found: no-such-place"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
