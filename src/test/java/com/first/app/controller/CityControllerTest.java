package com.first.app.controller;

import com.first.app.dto.CityResponse;
import com.first.app.dto.CitySummaryResponse;
import com.first.app.dto.PageResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.CityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CityController.class)
@AutoConfigureMockMvc(addFilters = false)
class CityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CityService cityService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void list_returns200WithPageEnvelope() throws Exception {
        CitySummaryResponse item = CitySummaryResponse.builder()
                .id(1L)
                .slug("beijing")
                .name("Beijing")
                .nameZh("北京")
                .coverImageUrl("https://example.com/beijing.jpg")
                .bestSeason("September–October & April–May")
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
        when(cityService.list(0, 20))
                .thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("beijing"))
                .andExpect(jsonPath("$.content[0].name").value("Beijing"))
                .andExpect(jsonPath("$.content[0].nameZh").value("北京"))
                .andExpect(jsonPath("$.content[0].bestSeason").value("September–October & April–May"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void list_forwardsPaginationParameters() throws Exception {
        when(cityService.list(1, 10))
                .thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));

        mockMvc.perform(get("/api/cities?page=1&size=10"))
                .andExpect(status().isOk());

        verify(cityService).list(1, 10);
    }

    @Test
    void list_invalidParameters_return400ErrorBody() throws Exception {
        when(cityService.list(-1, 20))
                .thenThrow(new InvalidRequestException("page must not be negative"));
        mockMvc.perform(get("/api/cities?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page must not be negative"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());

        when(cityService.list(0, 0))
                .thenThrow(new InvalidRequestException("size must be between 1 and 100"));
        mockMvc.perform(get("/api/cities?size=0"))
                .andExpect(status().isBadRequest());

        when(cityService.list(0, 101))
                .thenThrow(new InvalidRequestException("size must be between 1 and 100"));
        mockMvc.perform(get("/api/cities?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void detail_returns200WithDescriptionAndAttractionCount() throws Exception {
        CityResponse detail = CityResponse.builder()
                .id(1L)
                .slug("beijing")
                .name("Beijing")
                .nameZh("北京")
                .coverImageUrl("https://example.com/beijing.jpg")
                .bestSeason("September–October & April–May")
                .description("Capital of China with imperial landmarks.")
                .attractionCount(4L)
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 10, 0))
                .build();
        when(cityService.getBySlug("beijing")).thenReturn(detail);

        mockMvc.perform(get("/api/cities/beijing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("beijing"))
                .andExpect(jsonPath("$.nameZh").value("北京"))
                .andExpect(jsonPath("$.bestSeason").value("September–October & April–May"))
                .andExpect(jsonPath("$.description").value("Capital of China with imperial landmarks."))
                .andExpect(jsonPath("$.attractionCount").value(4));
    }

    @Test
    void detail_unknownSlug_returns404ErrorBody() throws Exception {
        when(cityService.getBySlug("no-such-city"))
                .thenThrow(new ResourceNotFoundException("City not found: no-such-city"));

        mockMvc.perform(get("/api/cities/no-such-city"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("City not found: no-such-city"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
