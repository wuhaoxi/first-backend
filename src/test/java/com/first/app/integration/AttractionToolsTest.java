package com.first.app.integration;

import com.first.app.service.AttractionSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttractionToolsTest {

    @Mock
    private AttractionSearchService attractionSearchService;

    private AttractionTools attractionTools;

    @BeforeEach
    void setUp() {
        attractionTools = new AttractionTools(attractionSearchService);
    }

    @Test
    void searchAttractions_returnsServiceTextUnchanged() {
        String serviceText = "[{\"nameZh\":\"西湖\",\"name\":\"West Lake\"}]";
        when(attractionSearchService.search("杭州", "NATURE", "湖", 3)).thenReturn(serviceText);

        String result = attractionTools.searchAttractions("杭州", "NATURE", "湖", 3);

        assertThat(result).isEqualTo(serviceText);
        verify(attractionSearchService).search("杭州", "NATURE", "湖", 3);
    }

    @Test
    void searchAttractions_noMatchNotice_passesThroughUnchanged() {
        String notice = "No published attractions matched this search.";
        when(attractionSearchService.search(null, null, null, null)).thenReturn(notice);

        assertThat(attractionTools.searchAttractions(null, null, null, null)).isEqualTo(notice);
    }

    @Test
    void toolAnnotation_declaresSearchAttractionsWithOptionalFilters() throws NoSuchMethodException {
        Method method = AttractionTools.class.getMethod("searchAttractions", String.class,
                String.class, String.class, Integer.class);

        Tool tool = method.getAnnotation(Tool.class);
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("search_attractions");

        Parameter[] parameters = method.getParameters();
        assertThat(parameters).hasSize(4);
        assertThat(Arrays.stream(parameters).map(Parameter::getName).toList())
                .containsExactly("city", "category", "keyword", "limit");
        for (Parameter parameter : parameters) {
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            assertThat(toolParam).isNotNull();
            assertThat(toolParam.required()).isFalse();
        }
    }
}
