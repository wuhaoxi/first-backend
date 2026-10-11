package com.first.app.controller;

import com.first.app.dto.AssistantChatRequest;
import com.first.app.dto.AssistantEvent;
import com.first.app.exception.UnauthenticatedException;
import com.first.app.service.AssistantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Thin HTTP shell of the assistant chat endpoint (work item 5.x of add-ai-assistant): the
 * authenticated {@code userId} attribute maps straight to the service call and the service's
 * {@code Flux<ServerSentEvent>} is returned as-is — MVC's reactive handler streams it, no
 * {@code SseEmitter} threading. Guard failures (400/401/429/503) surface through
 * {@code GlobalExceptionHandler} before any SSE byte is written.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping(value = "/assistant/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AssistantEvent>> chat(@RequestBody AssistantChatRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            throw new UnauthenticatedException("Authentication required");
        }
        httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return assistantService.stream(userId, request);
    }
}
