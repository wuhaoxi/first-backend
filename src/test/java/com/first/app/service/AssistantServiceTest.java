package com.first.app.service;

import com.first.app.config.AssistantProperties;
import com.first.app.dto.AssistantChatRequest;
import com.first.app.dto.AssistantEvent;
import com.first.app.exception.AssistantUnavailableException;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    private static final long USER_ID = 7L;
    private static final String CONVERSATION_ID = "5f0c2d0e-1c3a-4f0e-9a4b-2f6d8e1a7b3c";
    private static final String SANITIZED_ERROR =
            "The assistant is temporarily unavailable. Please try again.";

    @Mock
    private ConversationStore conversationStore;

    @Mock
    private AssistantRateLimiter rateLimiter;

    private final AssistantProperties properties =
            new AssistantProperties("sk-test", Duration.ofHours(24), 20, 6000, 2000, 10);

    /** Cross-component call order: "append:{ROLE}" from the store stub, "stream" from the fake model. */
    private final List<String> sequence = new ArrayList<>();

    private FakeChatModel model;
    private ChatClient chatClient;
    private AssistantService service;

    @BeforeEach
    void setUp() {
        model = new FakeChatModel(sequence);
        chatClient = ChatClient.create(model);
        service = new AssistantService(chatClient, conversationStore, rateLimiter, properties);
        lenient().when(rateLimiter.tryAcquire(USER_ID)).thenReturn(true);
        lenient().when(conversationStore.load(anyLong(), anyString())).thenReturn(List.of());
        lenient().doAnswer(invocation -> {
            ConversationStore.Turn turn = invocation.getArgument(2);
            sequence.add("append:" + turn.role());
            return null;
        }).when(conversationStore).append(anyLong(), anyString(), any());
    }

    @Test
    void stream_blankMessage_throwsInvalidRequestBeforeAnyInteraction() {
        assertThatThrownBy(() -> service.stream(USER_ID, new AssistantChatRequest("   ", null)))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(conversationStore, rateLimiter);
    }

    @Test
    void stream_messageOverMaxLength_throwsInvalidRequestBeforeAnyInteraction() {
        String tooLong = "x".repeat(2001);

        assertThatThrownBy(() -> service.stream(USER_ID, new AssistantChatRequest(tooLong, null)))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(conversationStore, rateLimiter);
    }

    @Test
    void stream_malformedConversationId_throwsInvalidRequestBeforeAnyInteraction() {
        assertThatThrownBy(() ->
                service.stream(USER_ID, new AssistantChatRequest("hello", "not-a-uuid")))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(conversationStore, rateLimiter);
    }

    @Test
    void stream_unconfiguredApiKey_throwsUnavailableBeforeAnyInteraction() {
        AssistantService unconfigured = new AssistantService(chatClient, conversationStore, rateLimiter,
                new AssistantProperties("", Duration.ofHours(24), 20, 6000, 2000, 10));

        assertThatThrownBy(() -> unconfigured.stream(USER_ID, new AssistantChatRequest("hello", null)))
                .isInstanceOf(AssistantUnavailableException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(conversationStore, rateLimiter);
    }

    @Test
    void stream_rateLimited_throwsRateLimitWithoutTouchingHistoryOrModel() {
        when(rateLimiter.tryAcquire(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.stream(USER_ID, new AssistantChatRequest("hello", null)))
                .isInstanceOf(RateLimitExceededException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(conversationStore);
    }

    @Test
    void stream_emitsDeltasInOrderThenDoneEchoingConversationId() {
        model.emitting("Beijing", " is ", "great");

        List<ServerSentEvent<AssistantEvent>> events =
                run(service.stream(USER_ID, new AssistantChatRequest("weather?", CONVERSATION_ID)));

        assertThat(events).hasSize(4);
        assertThat(events.get(0).event()).isEqualTo("delta");
        assertThat(((AssistantEvent.Delta) events.get(0).data()).text()).isEqualTo("Beijing");
        assertThat(events.get(1).event()).isEqualTo("delta");
        assertThat(((AssistantEvent.Delta) events.get(1).data()).text()).isEqualTo(" is ");
        assertThat(events.get(2).event()).isEqualTo("delta");
        assertThat(((AssistantEvent.Delta) events.get(2).data()).text()).isEqualTo("great");
        assertThat(events.get(3).event()).isEqualTo("done");
        assertThat(((AssistantEvent.Done) events.get(3).data()).conversationId())
                .isEqualTo(CONVERSATION_ID);
    }

    @Test
    void stream_blankConversationId_generatesUuidEchoedInDone() {
        List<ServerSentEvent<AssistantEvent>> events =
                run(service.stream(USER_ID, new AssistantChatRequest("hello", "   ")));

        ServerSentEvent<AssistantEvent> done = events.get(events.size() - 1);
        assertThat(done.event()).isEqualTo("done");
        String conversationId = ((AssistantEvent.Done) done.data()).conversationId();
        assertThat(conversationId).isNotBlank();
        assertThatCode(() -> UUID.fromString(conversationId)).doesNotThrowAnyException();
    }

    @Test
    void stream_promptContainsSystemPromptThenHistoryThenUserMessage() {
        when(conversationStore.load(USER_ID, CONVERSATION_ID)).thenReturn(List.of(
                new ConversationStore.Turn("USER", "My name is Alex", "2026-10-07T09:00:00Z"),
                new ConversationStore.Turn("ASSISTANT", "Nice to meet you, Alex", "2026-10-07T09:00:01Z")));

        run(service.stream(USER_ID, new AssistantChatRequest("What is my name?", CONVERSATION_ID)));

        assertThat(model.prompts).hasSize(1);
        List<Message> instructions = model.prompts.get(0).getInstructions();
        assertThat(instructions).hasSize(4);
        assertThat(instructions.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(instructions.get(0).getText()).isEqualTo(AssistantService.SYSTEM_PROMPT);
        assertThat(instructions.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(instructions.get(1).getText()).isEqualTo("My name is Alex");
        assertThat(instructions.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(instructions.get(2).getText()).isEqualTo("Nice to meet you, Alex");
        assertThat(instructions.get(3).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(instructions.get(3).getText()).isEqualTo("What is my name?");
    }

    @Test
    void stream_persistsUserTurnBeforeCallAndAssistantTurnAfterCompletion() {
        model.emitting("Hello", " world");

        run(service.stream(USER_ID, new AssistantChatRequest("hi", CONVERSATION_ID)));

        assertThat(sequence).containsExactly("append:USER", "stream", "append:ASSISTANT");
        ArgumentCaptor<ConversationStore.Turn> turns = ArgumentCaptor.forClass(ConversationStore.Turn.class);
        verify(conversationStore, times(2)).append(eq(USER_ID), eq(CONVERSATION_ID), turns.capture());
        assertThat(turns.getAllValues().get(0).role()).isEqualTo("USER");
        assertThat(turns.getAllValues().get(0).content()).isEqualTo("hi");
        assertThat(turns.getAllValues().get(1).role()).isEqualTo("ASSISTANT");
        assertThat(turns.getAllValues().get(1).content()).isEqualTo("Hello world");
    }

    @Test
    void stream_midStreamError_emitsSanitizedTerminalErrorAndKeepsOnlyUserTurn() {
        model.emitting("Hello").thenFailingWith(new RuntimeException("upstream 500 secret detail"));

        List<ServerSentEvent<AssistantEvent>> events =
                run(service.stream(USER_ID, new AssistantChatRequest("hi", CONVERSATION_ID)));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).event()).isEqualTo("delta");
        assertThat(events.get(1).event()).isEqualTo("error");
        String message = ((AssistantEvent.Error) events.get(1).data()).message();
        assertThat(message).isEqualTo(SANITIZED_ERROR);
        assertThat(message).doesNotContain("secret detail");

        ArgumentCaptor<ConversationStore.Turn> turns = ArgumentCaptor.forClass(ConversationStore.Turn.class);
        verify(conversationStore).append(eq(USER_ID), eq(CONVERSATION_ID), turns.capture());
        assertThat(turns.getValue().role()).isEqualTo("USER");
    }

    @Test
    void stream_cancelledMidStream_doesNotPersistAssistantTurn() {
        model.emitting("Hello").thenHanging();

        List<ServerSentEvent<AssistantEvent>> received = new ArrayList<>();
        Disposable subscription = service
                .stream(USER_ID, new AssistantChatRequest("hi", CONVERSATION_ID))
                .subscribe(received::add);

        subscription.dispose();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).event()).isEqualTo("delta");
        assertThat(sequence).containsExactly("append:USER", "stream");
        ArgumentCaptor<ConversationStore.Turn> turns = ArgumentCaptor.forClass(ConversationStore.Turn.class);
        verify(conversationStore, times(1)).append(eq(USER_ID), eq(CONVERSATION_ID), turns.capture());
        assertThat(turns.getValue().role()).isEqualTo("USER");
    }

    @Test
    void stream_redisUnavailable_stillStreamsWithDegradedHistory() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        ConversationStore degradingStore = new ConversationStore(redisTemplate,
                Jackson2ObjectMapperBuilder.json().build(), properties);
        AssistantService degradingService =
                new AssistantService(chatClient, degradingStore, rateLimiter, properties);

        List<ServerSentEvent<AssistantEvent>> events =
                run(degradingService.stream(USER_ID, new AssistantChatRequest("hi", CONVERSATION_ID)));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).event()).isEqualTo("delta");
        assertThat(events.get(1).event()).isEqualTo("done");
    }

    private static List<ServerSentEvent<AssistantEvent>> run(Flux<ServerSentEvent<AssistantEvent>> flux) {
        return flux.collectList().block();
    }

    /** Hand-rolled model: records prompts, emits chunks on the subscriber thread, then completes/errors/hangs. */
    private static final class FakeChatModel implements ChatModel {

        private final List<Prompt> prompts = new ArrayList<>();
        private final List<String> sequence;
        private List<String> chunks = List.of("Hello");
        private Throwable error;
        private boolean hangAfterChunks;

        FakeChatModel(List<String> sequence) {
            this.sequence = sequence;
        }

        FakeChatModel emitting(String... chunks) {
            this.chunks = List.of(chunks);
            return this;
        }

        FakeChatModel thenFailingWith(Throwable error) {
            this.error = error;
            return this;
        }

        FakeChatModel thenHanging() {
            this.hangAfterChunks = true;
            return this;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("call() is not used by the streaming service");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            prompts.add(prompt);
            sequence.add("stream");
            Flux<ChatResponse> responses = Flux.fromIterable(chunks)
                    .map(text -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
            if (error != null) {
                return responses.concatWith(Flux.error(error));
            }
            if (hangAfterChunks) {
                return responses.concatWith(Flux.never());
            }
            return responses;
        }
    }
}
