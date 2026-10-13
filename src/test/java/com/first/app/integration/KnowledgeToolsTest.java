package com.first.app.integration;

import com.first.app.service.KnowledgeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeToolsTest {

    @Mock
    private KnowledgeSearchService knowledgeSearchService;

    private KnowledgeTools knowledgeTools;

    @BeforeEach
    void setUp() {
        knowledgeTools = new KnowledgeTools(knowledgeSearchService);
    }

    @Test
    void searchKnowledgeBase_returnsServiceTextUnchanged() {
        String serviceText = "[1] 《西湖》 (attractions)\nWest Lake 著名景点";
        when(knowledgeSearchService.search("杭州有哪些景点？")).thenReturn(serviceText);

        String result = knowledgeTools.searchKnowledgeBase("杭州有哪些景点？");

        assertThat(result).isEqualTo(serviceText);
        verify(knowledgeSearchService).search("杭州有哪些景点？");
    }

    @Test
    void searchKnowledgeBase_unavailableNotice_passesThroughUnchanged() {
        String notice =
                "Knowledge base is currently unavailable; site content cannot be searched right now.";
        when(knowledgeSearchService.search("q")).thenReturn(notice);

        assertThat(knowledgeTools.searchKnowledgeBase("q")).isEqualTo(notice);
    }

    @Test
    void toolAnnotation_declaresSearchKnowledgeBaseWithRequiredQuestion() throws NoSuchMethodException {
        Method method = KnowledgeTools.class.getMethod("searchKnowledgeBase", String.class);

        Tool tool = method.getAnnotation(Tool.class);
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("search_knowledge_base");

        Parameter question = method.getParameters()[0];
        ToolParam toolParam = question.getAnnotation(ToolParam.class);
        assertThat(toolParam).isNotNull();
        assertThat(toolParam.required()).isTrue();
    }
}
