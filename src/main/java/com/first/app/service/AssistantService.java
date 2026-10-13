package com.first.app.service;

import com.first.app.config.AssistantProperties;
import com.first.app.dto.AssistantChatRequest;
import com.first.app.dto.AssistantEvent;
import com.first.app.exception.AssistantUnavailableException;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The assistant chat core (work item 4.x of add-ai-assistant): validate → gate → rate → load
 * windowed history → persist the user turn → stream the model reply as
 * {@code delta*} + terminal {@code done}/{@code error} SSE events. The assistant turn is
 * persisted only when the stream completes successfully with non-empty text; a mid-stream
 * failure or a client cancel leaves history with the user turn only.
 *
 * <p>Guards run synchronously before the flux is handed out, so 400/429/503 render as plain
 * JSON error responses before any SSE byte is written. Logs never carry message bodies.
 */
@Slf4j
@Service
public class AssistantService {

    /** Travel-assistant persona for WanderChina; mirrors the user's language and never invents live data. */
    public static final String SYSTEM_PROMPT = """
            You are the WanderChina travel assistant embedded on the homepage. Answer travel \
            questions about destinations in China: live weather, exchange rates, and practical \
            tips. Mirror the language the user writes in. Be concise. Use the get_weather and \
            get_exchange_rate tools whenever live figures are needed — never invent live data. If \
            a tool returns an error envelope, tell the user the data is currently unavailable and \
            offer to try again. For structured facts about attractions — lists, ticket prices, \
            opening hours, booking requirements — use the search_attractions tool and ground the \
            answer in its results. For prose about site content — attraction guides, city \
            overviews, or travel posts — use the search_knowledge_base tool. When an answer uses \
            search_attractions or search_knowledge_base results, end the reply with a plain-text \
            source list in the exact form Sources: [n] 《title》 (type) — tag attraction-search \
            sources as (attractions) — listing only the sources actually used. Never output URLs, \
            Markdown links, or HTML. When no tool result was used, add no source list.""";

    /** Sanitized terminal error text; upstream details never reach the client. */
    private static final String UNAVAILABLE_MESSAGE =
            "The assistant is temporarily unavailable. Please try again.";

    private final ChatClient chatClient;
    private final ConversationStore conversationStore;
    private final AssistantRateLimiter rateLimiter;
    private final AssistantProperties properties;

    public AssistantService(ChatClient chatClient, ConversationStore conversationStore,
            AssistantRateLimiter rateLimiter, AssistantProperties properties) {
        this.chatClient = chatClient;
        this.conversationStore = conversationStore;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /**
     * Validates the request, enforces the config gate and rate limit, persists the user turn,
     * and returns the streaming response. Throws {@link InvalidRequestException},
     * {@link AssistantUnavailableException} or {@link RateLimitExceededException} before any
     * model or Redis interaction when the corresponding guard trips.
     */
    public Flux<ServerSentEvent<AssistantEvent>> stream(long userId, AssistantChatRequest request) {
        String message = validatedMessage(request.message());
        String conversationId = resolveConversationId(request.conversationId());
        if (!properties.configured()) {
            throw new AssistantUnavailableException("The assistant is not configured");
        }
        if (!rateLimiter.tryAcquire(userId)) {
            throw new RateLimitExceededException("Too many assistant requests — try again in a minute");
        }

        List<ConversationStore.Turn> history = conversationStore.load(userId, conversationId);
        conversationStore.append(userId, conversationId, ConversationStore.Turn.user(message));

        StringBuilder fullReply = new StringBuilder();
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(toMessages(history, message))
                .stream()
                .chatResponse()
                .map(AssistantService::chunkText)
                .filter(StringUtils::hasText)
                .map(text -> {
                    fullReply.append(text);
                    return deltaEvent(text);
                })
                .concatWith(Flux.just(doneEvent(conversationId)))
                .doOnComplete(() -> persistAssistantTurn(userId, conversationId, fullReply.toString()))
                .onErrorResume(e -> {
                    log.warn("Assistant stream failed for conversation {}: {}", conversationId, e.getMessage());
                    return Flux.just(errorEvent());
                });
    }

    private String validatedMessage(String raw) {
        String message = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(message)) {
            throw new InvalidRequestException("message must not be blank");
        }
        if (message.length() > properties.maxMessageLength()) {
            throw new InvalidRequestException(
                    "message must be at most " + properties.maxMessageLength() + " characters");
        }
        return message;
    }

    private static String resolveConversationId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return UUID.randomUUID().toString();
        }
        try {
            UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("conversationId must be a UUID");
        }
        return raw;
    }

    private static List<Message> toMessages(List<ConversationStore.Turn> history, String message) {
        List<Message> messages = new ArrayList<>(history.size() + 1);
        for (ConversationStore.Turn turn : history) {
            if (ConversationStore.Turn.ROLE_USER.equals(turn.role())) {
                messages.add(new UserMessage(turn.content()));
            } else if (ConversationStore.Turn.ROLE_ASSISTANT.equals(turn.role())) {
                messages.add(new AssistantMessage(turn.content()));
            }
        }
        messages.add(new UserMessage(message));
        return messages;
    }

    private void persistAssistantTurn(long userId, String conversationId, String fullReply) {
        if (StringUtils.hasText(fullReply)) {
            conversationStore.append(userId, conversationId, ConversationStore.Turn.assistant(fullReply));
        }
    }

    private static String chunkText(ChatResponse response) {
        return response == null || response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();
    }

    private static ServerSentEvent<AssistantEvent> deltaEvent(String text) {
        return ServerSentEvent.<AssistantEvent>builder()
                .event("delta")
                .data(new AssistantEvent.Delta(text))
                .build();
    }

    private static ServerSentEvent<AssistantEvent> doneEvent(String conversationId) {
        return ServerSentEvent.<AssistantEvent>builder()
                .event("done")
                .data(new AssistantEvent.Done(conversationId))
                .build();
    }

    private static ServerSentEvent<AssistantEvent> errorEvent() {
        return ServerSentEvent.<AssistantEvent>builder()
                .event("error")
                .data(new AssistantEvent.Error(UNAVAILABLE_MESSAGE))
                .build();
    }
}
