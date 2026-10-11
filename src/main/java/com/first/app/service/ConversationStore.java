package com.first.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.config.AssistantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis-backed assistant conversation memory (work item 2.x of add-ai-assistant).
 *
 * <p>Every exchange persists as JSON under {@code assistant:conversation:{userId}:{conversationId}}
 * with a sliding {@code app.assistant.history-ttl} refresh on each append. History is windowed
 * to the newest turns within both the message-count and char budgets, never starting with an
 * ASSISTANT turn so the prompt always opens on the user. Redis failures degrade to an empty
 * history instead of throwing — conversation memory never blocks a chat reply.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AssistantProperties properties;

    /** One persisted turn; {@code at} is an ISO-8601 instant stamped by the factories. */
    public record Turn(String role, String content, String at) {

        public static final String ROLE_USER = "USER";
        public static final String ROLE_ASSISTANT = "ASSISTANT";

        public static Turn user(String content) {
            return new Turn(ROLE_USER, content, Instant.now().toString());
        }

        public static Turn assistant(String content) {
            return new Turn(ROLE_ASSISTANT, content, Instant.now().toString());
        }
    }

    private record StoredConversation(List<Turn> messages) {
    }

    /** Returns the windowed history, oldest first; empty on absent/corrupt data or Redis failure. */
    public List<Turn> load(long userId, String conversationId) {
        try {
            return window(read(userId, conversationId));
        } catch (Exception e) {
            log.warn("Conversation history load failed for key {} — treating as empty: {}",
                    key(userId, conversationId), e.getMessage());
            return List.of();
        }
    }

    /** Read-modify-write append; the TTL is refreshed on every write. Never throws. */
    public void append(long userId, String conversationId, Turn turn) {
        String key = key(userId, conversationId);
        try {
            List<Turn> turns = new ArrayList<>(read(userId, conversationId));
            turns.add(turn);
            String json = objectMapper.writeValueAsString(new StoredConversation(window(turns)));
            redisTemplate.opsForValue().set(key, json, properties.historyTtl());
        } catch (Exception e) {
            log.warn("Conversation history append failed for key {} — turn dropped: {}",
                    key, e.getMessage());
        }
    }

    private List<Turn> read(long userId, String conversationId) {
        String json = redisTemplate.opsForValue().get(key(userId, conversationId));
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            StoredConversation stored = objectMapper.readValue(json, StoredConversation.class);
            return stored.messages() == null ? List.of() : stored.messages();
        } catch (Exception e) {
            log.warn("Corrupt conversation history at key {} — starting fresh: {}",
                    key(userId, conversationId), e.getMessage());
            return List.of();
        }
    }

    /** Newest turns within both budgets; drops leading ASSISTANT turns so history opens on a user turn. */
    private List<Turn> window(List<Turn> turns) {
        List<Turn> windowed = new ArrayList<>(turns);
        while (windowed.size() > properties.windowMessages()) {
            windowed.remove(0);
        }
        while (!windowed.isEmpty() && charCount(windowed) > properties.windowChars()) {
            windowed.remove(0);
        }
        while (!windowed.isEmpty() && Turn.ROLE_ASSISTANT.equals(windowed.get(0).role())) {
            windowed.remove(0);
        }
        return List.copyOf(windowed);
    }

    private static int charCount(List<Turn> turns) {
        return turns.stream().mapToInt(t -> t.content() == null ? 0 : t.content().length()).sum();
    }

    private static String key(long userId, String conversationId) {
        return "assistant:conversation:" + userId + ":" + conversationId;
    }
}
