package com.first.app.controller;

import com.first.app.dto.AssistantChatRequest;
import com.first.app.dto.AssistantEvent;
import com.first.app.exception.AssistantUnavailableException;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.RateLimitExceededException;
import com.first.app.exception.UnauthenticatedException;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.AssistantService;
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

@WebMvcTest(AssistantController.class)
@AutoConfigureMockMvc(addFilters = false)
class AssistantControllerTest {

    private static final String CONVERSATION_ID = "5f0c2d0e-1c3a-4f0e-9a4b-2f6d8e1a7b3c";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssistantService assistantService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    @Test
    void chat_streamsDeltaFramesThenTerminalDone() throws Exception {
        when(assistantService.stream(eq(1L), any(AssistantChatRequest.class))).thenReturn(Flux.just(
                sse("delta", new AssistantEvent.Delta("Hello ")),
                sse("delta", new AssistantEvent.Delta("world")),
                sse("done", new AssistantEvent.Done(CONVERSATION_ID))));

        MvcResult result = mockMvc.perform(post("/api/assistant/chat")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("data:{\"text\":\"Hello \"}")))
                .andExpect(content().string(containsString("data:{\"text\":\"world\"}")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(
                        containsString("\"conversationId\":\"" + CONVERSATION_ID + "\"")));
    }

    @Test
    void chat_missingUserId_returns401() throws Exception {
        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void chat_blankMessage_returns400() throws Exception {
        when(assistantService.stream(eq(1L), any(AssistantChatRequest.class)))
                .thenThrow(new InvalidRequestException("message must not be blank"));

        mockMvc.perform(post("/api/assistant/chat")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("message must not be blank"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void chat_rateLimited_returns429() throws Exception {
        when(assistantService.stream(eq(1L), any(AssistantChatRequest.class)))
                .thenThrow(new RateLimitExceededException("Too many assistant requests"));

        mockMvc.perform(post("/api/assistant/chat")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many assistant requests"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void chat_unconfigured_returns503() throws Exception {
        when(assistantService.stream(eq(1L), any(AssistantChatRequest.class)))
                .thenThrow(new AssistantUnavailableException("The assistant is not configured"));

        mockMvc.perform(post("/api/assistant/chat")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The assistant is not configured"))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void chat_forwardsUserIdAndConversationIdToService() throws Exception {
        when(assistantService.stream(eq(7L), any(AssistantChatRequest.class)))
                .thenReturn(Flux.just(sse("done", new AssistantEvent.Done(CONVERSATION_ID))));

        MvcResult result = mockMvc.perform(post("/api/assistant/chat")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"conversationId\":\""
                                + CONVERSATION_ID + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        ArgumentCaptor<AssistantChatRequest> request = ArgumentCaptor.forClass(AssistantChatRequest.class);
        verify(assistantService).stream(eq(7L), request.capture());
        assertThat(request.getValue().message()).isEqualTo("hello");
        assertThat(request.getValue().conversationId()).isEqualTo(CONVERSATION_ID);
    }

    private static ServerSentEvent<AssistantEvent> sse(String name, AssistantEvent data) {
        return ServerSentEvent.<AssistantEvent>builder().event(name).data(data).build();
    }
}
