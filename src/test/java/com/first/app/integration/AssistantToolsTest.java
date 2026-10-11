package com.first.app.integration;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantToolsTest {

    private static final String ERROR_JSON =
            "{\"error\":{\"kind\":\"input-rejection\",\"reason\":\"latitude must be between -90 and 90 (got 999)\"}}";

    @Mock
    private TravelMcpTools travelMcpTools;

    private AssistantTools assistantTools;

    @BeforeEach
    void setUp() {
        assistantTools = new AssistantTools(travelMcpTools);
    }

    @Test
    void getWeather_delegatesArgumentMapIncludingNulls() {
        when(travelMcpTools.getWeather(anyMap()))
                .thenReturn(CallToolResult.builder().addTextContent("weather-json").build());

        String result = assistantTools.getWeather(48.85, 2.35, null, null);

        assertThat(result).isEqualTo("weather-json");
        ArgumentCaptor<Map<String, Object>> arguments = mapCaptor();
        verify(travelMcpTools).getWeather(arguments.capture());
        assertThat(arguments.getValue())
                .hasSize(4)
                .containsEntry("latitude", 48.85)
                .containsEntry("longitude", 2.35)
                .containsEntry("forecastDays", null)
                .containsEntry("pastDays", null);
    }

    @Test
    void getWeather_returnsFirstTextContentBlockUnchanged() {
        when(travelMcpTools.getWeather(anyMap())).thenReturn(CallToolResult.builder()
                .addTextContent("first")
                .addTextContent("second")
                .build());

        assertThat(assistantTools.getWeather(1.0, 2.0, 7, 0)).isEqualTo("first");
    }

    @Test
    void getWeather_errorEnvelope_returnsTextUnchanged() {
        when(travelMcpTools.getWeather(anyMap())).thenReturn(CallToolResult.builder()
                .addTextContent(ERROR_JSON)
                .isError(true)
                .build());

        assertThat(assistantTools.getWeather(999.0, 2.0, null, null)).isEqualTo(ERROR_JSON);
    }

    @Test
    void getExchangeRate_delegatesArgumentMapIncludingNulls() {
        when(travelMcpTools.getExchangeRate(anyMap()))
                .thenReturn(CallToolResult.builder().addTextContent("rate-json").build());

        String result = assistantTools.getExchangeRate("USD", "CNY", null, null);

        assertThat(result).isEqualTo("rate-json");
        ArgumentCaptor<Map<String, Object>> arguments = mapCaptor();
        verify(travelMcpTools).getExchangeRate(arguments.capture());
        assertThat(arguments.getValue())
                .hasSize(4)
                .containsEntry("base", "USD")
                .containsEntry("quote", "CNY")
                .containsEntry("fromDate", null)
                .containsEntry("toDate", null);
    }

    @Test
    void getExchangeRate_withDates_forwardsDates() {
        when(travelMcpTools.getExchangeRate(anyMap()))
                .thenReturn(CallToolResult.builder().addTextContent("series-json").build());

        assistantTools.getExchangeRate("USD", "CNY", "2026-01-01", "2026-01-31");

        ArgumentCaptor<Map<String, Object>> arguments = mapCaptor();
        verify(travelMcpTools).getExchangeRate(arguments.capture());
        assertThat(arguments.getValue())
                .containsEntry("fromDate", "2026-01-01")
                .containsEntry("toDate", "2026-01-31");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
