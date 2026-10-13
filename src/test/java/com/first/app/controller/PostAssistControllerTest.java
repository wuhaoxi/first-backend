package com.first.app.controller;

import com.first.app.dto.PostAssistRequest;
import com.first.app.dto.PostPolishEvent;
import com.first.app.dto.TagSuggestionsResponse;
import com.first.app.dto.TitleSuggestionsResponse;
import com.first.app.exception.AssistantUnavailableException;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.RateLimitExceededException;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.PostAssistService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostAssistController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostAssistControllerTest {

    private static final String BODY = "{\"title\":\"My trip\",\"content\":\"A post\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostAssistService postAssistService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void suggestTitles_returnsSuggestionsJsonAndForwardsRequest() throws Exception {
        when(postAssistService.suggestTitles(eq(1L), any(PostAssistRequest.class)))
                .thenReturn(new TitleSuggestionsResponse(List.of("Option A", "Option B")));

        mockMvc.perform(post("/api/assistant/post-assist/title")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions.length()").value(2))
                .andExpect(jsonPath("$.suggestions[0]").value("Option A"));

        ArgumentCaptor<PostAssistRequest> captured = ArgumentCaptor.forClass(PostAssistRequest.class);
        verify(postAssistService).suggestTitles(eq(1L), captured.capture());
        assertThat(captured.getValue().title()).isEqualTo("My trip");
        assertThat(captured.getValue().content()).isEqualTo("A post");
    }

    @Test
    void suggestTags_returnsTagsJson() throws Exception {
        when(postAssistService.suggestTags(eq(1L), any(PostAssistRequest.class)))
                .thenReturn(new TagSuggestionsResponse(List.of("food", "history")));

        mockMvc.perform(post("/api/assistant/post-assist/tags")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[1]").value("history"));
    }

    @Test
    void suggestTitles_missingUserId_returns401() throws Exception {
        mockMvc.perform(post("/api/assistant/post-assist/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void suggestTitles_blankContent_returns400() throws Exception {
        when(postAssistService.suggestTitles(eq(1L), any(PostAssistRequest.class)))
                .thenThrow(new InvalidRequestException("content must not be blank"));

        mockMvc.perform(post("/api/assistant/post-assist/title")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("content must not be blank"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void suggestTitles_rateLimited_returns429() throws Exception {
        when(postAssistService.suggestTitles(eq(1L), any(PostAssistRequest.class)))
                .thenThrow(new RateLimitExceededException("Too many assistant requests"));

        mockMvc.perform(post("/api/assistant/post-assist/title")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void suggestTitles_unconfigured_returns503() throws Exception {
        when(postAssistService.suggestTitles(eq(1L), any(PostAssistRequest.class)))
                .thenThrow(new AssistantUnavailableException("The assistant is not configured"));

        mockMvc.perform(post("/api/assistant/post-assist/title")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void polish_streamsDeltaFramesThenTerminalDone() throws Exception {
        when(postAssistService.polish(eq(1L), any(PostAssistRequest.class))).thenReturn(Flux.just(
                sse("delta", new PostPolishEvent.Delta("Better ")),
                sse("done", new PostPolishEvent.Done())));

        MvcResult result = mockMvc.perform(post("/api/assistant/post-assist/polish")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("data:{\"text\":\"Better \"}")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("data:{}")));
    }

    @Test
    void polish_missingUserId_returns401() throws Exception {
        mockMvc.perform(post("/api/assistant/post-assist/polish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void polish_guardFailure_returnsJsonNotSse() throws Exception {
        when(postAssistService.polish(eq(1L), any(PostAssistRequest.class)))
                .thenThrow(new AssistantUnavailableException("The assistant is not configured"));

        mockMvc.perform(post("/api/assistant/post-assist/polish")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    private static ServerSentEvent<PostPolishEvent> sse(String name, PostPolishEvent data) {
        return ServerSentEvent.<PostPolishEvent>builder().event(name).data(data).build();
    }
}
