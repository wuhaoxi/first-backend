package com.first.app.controller;

import com.first.app.dto.PostAssistRequest;
import com.first.app.dto.PostPolishEvent;
import com.first.app.dto.TagSuggestionsResponse;
import com.first.app.dto.TitleSuggestionsResponse;
import com.first.app.exception.UnauthenticatedException;
import com.first.app.service.PostAssistService;
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
 * Thin HTTP shell of the post-assist endpoints (work item 8.x of add-post-ai-assist): the
 * authenticated {@code userId} attribute maps straight to the service call. Title and tag
 * suggestions answer with plain JSON; polish returns the service's
 * {@code Flux<ServerSentEvent>} as-is, streamed by MVC's reactive handler. Guard failures
 * (400/401/429/503) surface through {@code GlobalExceptionHandler} before any SSE byte is
 * written.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PostAssistController {

    private final PostAssistService postAssistService;

    @PostMapping("/assistant/post-assist/title")
    public TitleSuggestionsResponse suggestTitles(@RequestBody PostAssistRequest request,
            HttpServletRequest httpRequest) {
        return postAssistService.suggestTitles(authenticatedUserId(httpRequest), request);
    }

    @PostMapping("/assistant/post-assist/tags")
    public TagSuggestionsResponse suggestTags(@RequestBody PostAssistRequest request,
            HttpServletRequest httpRequest) {
        return postAssistService.suggestTags(authenticatedUserId(httpRequest), request);
    }

    @PostMapping(value = "/assistant/post-assist/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<PostPolishEvent>> polish(@RequestBody PostAssistRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Long userId = authenticatedUserId(httpRequest);
        httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return postAssistService.polish(userId, request);
    }

    private static long authenticatedUserId(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            throw new UnauthenticatedException("Authentication required");
        }
        return userId;
    }
}
