package com.first.app.service;

import com.first.app.config.AssistantProperties;
import com.first.app.dto.PostAssistRequest;
import com.first.app.dto.PostPolishEvent;
import com.first.app.dto.TagSuggestionsResponse;
import com.first.app.dto.TitleSuggestionsResponse;
import com.first.app.exception.AssistantUnavailableException;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Post-assist core: title suggestions, tag suggestions and body polish for post authors. Shares
 * the chat guard order — validate (mirroring {@code PostService} limits: content ≤60,000 UTF-8
 * bytes, title ≤200 chars) → configuration gate → rate limit — so 400/429/503 render as plain
 * JSON before any model call. Model output is sanitized (trimmed, blanks/overlong/duplicate
 * entries dropped) and one retry is attempted before the sanitized 503. The service holds no
 * conversation state and persists nothing; logs never carry post content.
 */
@Slf4j
@Service
public class PostAssistService {

    /** Title-suggestion system prompt: up to 3 single-line candidates as a strict JSON array. */
    static final String TITLE_SYSTEM_PROMPT = """
            You suggest titles for a travel post on WanderChina. Reply with up to 3 concise \
            candidate titles in the post content's language as a strict JSON array of strings and \
            nothing else — no code fences, no commentary. Each title must be a single line of at \
            most 200 characters.""";

    /** Tag-suggestion system prompt: up to 8 short comma-free tags as a strict JSON array. */
    static final String TAGS_SYSTEM_PROMPT = """
            You suggest tags for a travel post on WanderChina. Reply with up to 8 short tags in \
            the post content's language as a strict JSON array of strings and nothing else — no \
            code fences, no commentary, no commas, no '#'. Each tag must be at most 50 \
            characters.""";

    /** Polish system prompt: rewrite preserving facts, language, and Markdown structure. */
    static final String POLISH_SYSTEM_PROMPT = """
            You rewrite a travel post for clarity while preserving every fact, the post's \
            language, and its Markdown structure. Output only the rewritten Markdown — no \
            commentary, no code fences, no surrounding quotes.""";

    /** Sanitized terminal error text; upstream details never reach the client. */
    private static final String UNAVAILABLE_MESSAGE =
            "The assistant is temporarily unavailable. Please try again.";

    private static final String NOT_CONFIGURED_MESSAGE = "The assistant is not configured";

    private static final String RATE_LIMITED_MESSAGE =
            "Too many assistant requests — try again in a minute";

    private static final int MAX_CONTENT_BYTES = 60_000;

    private static final int MAX_TITLE_CHARS = 200;

    private static final int MAX_TITLES = 3;

    private static final int MAX_TAGS = 8;

    private static final int MAX_TAG_CHARS = 50;

    private static final int MAX_ATTEMPTS = 2;

    private final ChatClient chatClient;

    private final AssistantRateLimiter rateLimiter;

    private final AssistantProperties properties;

    public PostAssistService(@Qualifier("postAssistChatClient") ChatClient chatClient,
            AssistantRateLimiter rateLimiter, AssistantProperties properties) {
        this.chatClient = chatClient;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /** Suggests 1–3 sanitized titles; guards run before any model or Redis interaction. */
    public TitleSuggestionsResponse suggestTitles(long userId, PostAssistRequest request) {
        guard(userId, request);
        List<String> suggestions = generatedList(TITLE_SYSTEM_PROMPT, request, MAX_TITLES,
                MAX_TITLE_CHARS, false);
        return new TitleSuggestionsResponse(suggestions);
    }

    /** Suggests 1–8 sanitized tags (each ≤50 chars, no commas); same guards as titles. */
    public TagSuggestionsResponse suggestTags(long userId, PostAssistRequest request) {
        guard(userId, request);
        List<String> tags = generatedList(TAGS_SYSTEM_PROMPT, request, MAX_TAGS, MAX_TAG_CHARS, true);
        return new TagSuggestionsResponse(tags);
    }

    /**
     * Rewrites the post body, streaming polished text as {@code delta*} frames ended by exactly
     * one terminal {@code done} (success) or sanitized {@code error}. Guards run synchronously
     * before the flux is handed out, so 400/429/503 render as plain JSON. Nothing is persisted.
     */
    public Flux<ServerSentEvent<PostPolishEvent>> polish(long userId, PostAssistRequest request) {
        guard(userId, request);
        return chatClient.prompt()
                .system(POLISH_SYSTEM_PROMPT)
                .user(userPrompt(request))
                .stream()
                .content()
                .filter(StringUtils::hasText)
                .map(PostAssistService::deltaEvent)
                .concatWith(Flux.just(doneEvent()))
                .onErrorResume(e -> {
                    log.warn("Post-polish stream failed: {}", e.getMessage());
                    return Flux.just(errorEvent());
                });
    }

    private static ServerSentEvent<PostPolishEvent> deltaEvent(String text) {
        return ServerSentEvent.<PostPolishEvent>builder()
                .event("delta")
                .data(new PostPolishEvent.Delta(text))
                .build();
    }

    private static ServerSentEvent<PostPolishEvent> doneEvent() {
        return ServerSentEvent.<PostPolishEvent>builder()
                .event("done")
                .data(new PostPolishEvent.Done())
                .build();
    }

    private static ServerSentEvent<PostPolishEvent> errorEvent() {
        return ServerSentEvent.<PostPolishEvent>builder()
                .event("error")
                .data(new PostPolishEvent.Error(UNAVAILABLE_MESSAGE))
                .build();
    }

    private void guard(long userId, PostAssistRequest request) {
        validate(request);
        if (!properties.configured()) {
            throw new AssistantUnavailableException(NOT_CONFIGURED_MESSAGE);
        }
        if (!rateLimiter.tryAcquire(userId)) {
            throw new RateLimitExceededException(RATE_LIMITED_MESSAGE);
        }
    }

    private static void validate(PostAssistRequest request) {
        String content = request.content();
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("content must not be blank");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new InvalidRequestException(
                    "content must be at most " + MAX_CONTENT_BYTES + " UTF-8 bytes");
        }
        String title = request.title();
        if (title != null && title.length() > MAX_TITLE_CHARS) {
            throw new InvalidRequestException(
                    "title must be at most " + MAX_TITLE_CHARS + " characters");
        }
    }

    private List<String> generatedList(String systemPrompt, PostAssistRequest request, int maxCount,
            int maxChars, boolean rejectCommas) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<String> sanitized =
                    callAndSanitize(systemPrompt, request, maxCount, maxChars, rejectCommas);
            if (!sanitized.isEmpty()) {
                return sanitized;
            }
        }
        throw new AssistantUnavailableException(UNAVAILABLE_MESSAGE);
    }

    private List<String> callAndSanitize(String systemPrompt, PostAssistRequest request,
            int maxCount, int maxChars, boolean rejectCommas) {
        try {
            List<String> raw = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt(request))
                    .call()
                    .entity(new ParameterizedTypeReference<List<String>>() {
                    });
            return sanitize(raw, maxCount, maxChars, rejectCommas);
        } catch (RuntimeException e) {
            log.warn("Post-assist model reply unusable: {}", e.getMessage());
            return List.of();
        }
    }

    private static String userPrompt(PostAssistRequest request) {
        String title = request.title() == null ? "" : request.title().strip();
        if (title.isEmpty()) {
            return "Content:\n" + request.content();
        }
        return "Current title: " + title + "\n\nContent:\n" + request.content();
    }

    private static List<String> sanitize(List<String> raw, int maxCount, int maxChars,
            boolean rejectCommas) {
        List<String> sanitized = new ArrayList<>();
        if (raw == null) {
            return sanitized;
        }
        for (String candidate : raw) {
            if (candidate == null) {
                continue;
            }
            String trimmed = candidate.strip();
            if (trimmed.isEmpty() || trimmed.length() > maxChars) {
                continue;
            }
            if (rejectCommas && trimmed.contains(",")) {
                continue;
            }
            if (!sanitized.contains(trimmed)) {
                sanitized.add(trimmed);
            }
            if (sanitized.size() == maxCount) {
                break;
            }
        }
        return sanitized;
    }
}
