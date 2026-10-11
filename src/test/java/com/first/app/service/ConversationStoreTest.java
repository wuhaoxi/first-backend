package com.first.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.config.AssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationStoreTest {

    private static final long USER_ID = 42L;
    private static final String CONVERSATION_ID = "c1";
    private static final String KEY = "assistant:conversation:42:c1";
    private static final Duration TTL = Duration.ofHours(24);
    private static final String AT = "2026-10-07T10:00:00Z";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /** Mirrors the production ObjectMapper (Spring's builder, JavaTime module auto-registered). */
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private ConversationStore conversationStore;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        conversationStore = new ConversationStore(redisTemplate, objectMapper, properties(20, 6000));
    }

    @Test
    void load_existingHistory_returnsTurnsInOrder() throws Exception {
        when(valueOperations.get(KEY)).thenReturn(storedJson(
                turn("USER", "Hello", "2026-10-07T10:00:00Z"),
                turn("ASSISTANT", "Hi!", "2026-10-07T10:00:01Z")));

        List<ConversationStore.Turn> turns = conversationStore.load(USER_ID, CONVERSATION_ID);

        assertThat(turns).hasSize(2);
        assertThat(turns.get(0).role()).isEqualTo("USER");
        assertThat(turns.get(0).content()).isEqualTo("Hello");
        assertThat(turns.get(0).at()).isEqualTo("2026-10-07T10:00:00Z");
        assertThat(turns.get(1).role()).isEqualTo("ASSISTANT");
        assertThat(turns.get(1).content()).isEqualTo("Hi!");
    }

    @Test
    void load_absentKey_returnsEmpty() {
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(conversationStore.load(USER_ID, CONVERSATION_ID)).isEmpty();
        verify(valueOperations).get("assistant:conversation:42:c1");
    }

    @Test
    void load_corruptJson_returnsEmptyWithoutThrowing() {
        when(valueOperations.get(KEY)).thenReturn("{not-valid-json");

        assertThat(conversationStore.load(USER_ID, CONVERSATION_ID)).isEmpty();
    }

    @Test
    void load_redisReadFailure_returnsEmpty() {
        when(valueOperations.get(KEY)).thenThrow(new RuntimeException("redis down"));

        assertThat(conversationStore.load(USER_ID, CONVERSATION_ID)).isEmpty();
    }

    @Test
    void load_oversizedHistory_trimsToWindowStartingAtUser() throws Exception {
        ConversationStore store = new ConversationStore(redisTemplate, objectMapper, properties(5, 6000));
        when(valueOperations.get(KEY)).thenReturn(storedJson(
                turn("USER", "c1"), turn("ASSISTANT", "c2"),
                turn("USER", "c3"), turn("ASSISTANT", "c4"),
                turn("USER", "c5"), turn("ASSISTANT", "c6"),
                turn("USER", "c7"), turn("ASSISTANT", "c8")));

        List<ConversationStore.Turn> turns = store.load(USER_ID, CONVERSATION_ID);

        assertThat(turns).hasSize(4);
        assertThat(turns.get(0).role()).isEqualTo("USER");
        assertThat(turns.get(0).content()).isEqualTo("c5");
        assertThat(turns.get(3).content()).isEqualTo("c8");
    }

    @Test
    void load_historyOverCharBudget_keepsNewestTurnsOnly() throws Exception {
        ConversationStore store = new ConversationStore(redisTemplate, objectMapper, properties(20, 10));
        when(valueOperations.get(KEY)).thenReturn(storedJson(
                turn("USER", "12345"), turn("ASSISTANT", "67890"), turn("USER", "abcde")));

        List<ConversationStore.Turn> turns = store.load(USER_ID, CONVERSATION_ID);

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).role()).isEqualTo("USER");
        assertThat(turns.get(0).content()).isEqualTo("abcde");
    }

    @Test
    void append_absentKey_storesSingleTurnWithFreshTtl() throws Exception {
        when(valueOperations.get(KEY)).thenReturn(null);

        conversationStore.append(USER_ID, CONVERSATION_ID, turn("USER", "Hello"));

        List<ConversationStore.Turn> stored = captureStored();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).role()).isEqualTo("USER");
        assertThat(stored.get(0).content()).isEqualTo("Hello");
        assertThat(stored.get(0).at()).isEqualTo(AT);
    }

    @Test
    void append_existingHistory_appendsTurnAndRefreshesTtl() throws Exception {
        when(valueOperations.get(KEY)).thenReturn(storedJson(turn("USER", "Hello")));

        conversationStore.append(USER_ID, CONVERSATION_ID, turn("ASSISTANT", "Hi!"));

        List<ConversationStore.Turn> stored = captureStored();
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).content()).isEqualTo("Hello");
        assertThat(stored.get(1).role()).isEqualTo("ASSISTANT");
        assertThat(stored.get(1).content()).isEqualTo("Hi!");
    }

    @Test
    void append_exceedingWindow_storesOnlyWindowedTurns() throws Exception {
        ConversationStore store = new ConversationStore(redisTemplate, objectMapper, properties(2, 6000));
        when(valueOperations.get(KEY)).thenReturn(storedJson(
                turn("USER", "c1"), turn("ASSISTANT", "c2")));

        store.append(USER_ID, CONVERSATION_ID, turn("USER", "c3"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), json.capture(), eq(TTL));
        assertThat(readStored(json.getValue())).hasSize(1);
        assertThat(readStored(json.getValue()).get(0).content()).isEqualTo("c3");
    }

    @Test
    void append_corruptExistingJson_startsFreshWithNewTurn() throws Exception {
        when(valueOperations.get(KEY)).thenReturn("{not-valid-json");

        conversationStore.append(USER_ID, CONVERSATION_ID, turn("USER", "Hello"));

        List<ConversationStore.Turn> stored = captureStored();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).content()).isEqualTo("Hello");
    }

    @Test
    void append_redisWriteFailure_doesNotThrow() throws Exception {
        when(valueOperations.get(KEY)).thenReturn(null);
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(eq(KEY), anyString(), any(Duration.class));

        assertThatCode(() -> conversationStore.append(USER_ID, CONVERSATION_ID, turn("USER", "Hello")))
                .doesNotThrowAnyException();
    }

    /** Verifies the Redis write happened with the configured TTL and returns the persisted turns. */
    private List<ConversationStore.Turn> captureStored() throws Exception {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), json.capture(), eq(TTL));
        return readStored(json.getValue());
    }

    private List<ConversationStore.Turn> readStored(String json) throws Exception {
        return objectMapper.readValue(json, Stored.class).messages();
    }

    private AssistantProperties properties(int windowMessages, int windowChars) {
        return new AssistantProperties("sk-test", TTL, windowMessages, windowChars, 2000, 10);
    }

    private ConversationStore.Turn turn(String role, String content) {
        return turn(role, content, AT);
    }

    private ConversationStore.Turn turn(String role, String content, String at) {
        return new ConversationStore.Turn(role, content, at);
    }

    private String storedJson(ConversationStore.Turn... turns) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("messages", List.of(turns)));
    }

    /** Mirrors the persisted JSON envelope: {@code {"messages":[{role, content, at}, ...]}}. */
    private record Stored(List<ConversationStore.Turn> messages) {
    }
}
