package com.first.app.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the 502 branch of {@link GlobalExceptionHandler} in isolation: an
 * {@link UpstreamUnavailableException} thrown anywhere in the MVC layer renders the standard
 * {@code ErrorResponse} body ({@code message}, {@code status: 502}, {@code timestamp}) — never a
 * hand-built error JSON.
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/upstream-unavailable")
        void upstreamUnavailable() {
            throw new UpstreamUnavailableException("Open-Meteo refused the connection");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @Test
    void upstreamUnavailable_renders502WithStandardErrorBody() throws Exception {
        mockMvc.perform(get("/probe/upstream-unavailable"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Open-Meteo refused the connection"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
