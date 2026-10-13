package com.first.app.service;

import com.first.app.config.AssistantProperties;
import com.first.app.dto.PostAssistRequest;
import com.first.app.dto.PostPolishEvent;
import com.first.app.dto.TagSuggestionsResponse;
import com.first.app.dto.TitleSuggestionsResponse;
import com.first.app.exception.AssistantUnavailableException;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostAssistServiceTest {

    private static final long USER_ID = 7L;
    private static final String SANITIZED_UNAVAILABLE =
            "The assistant is temporarily unavailable. Please try again.";
    private static final String VALID_CONTENT = "A short travel post about Chengdu's teahouses.";

    @Mock
    private AssistantRateLimiter rateLimiter;

    private final AssistantProperties properties =
            new AssistantProperties("sk-test", Duration.ofHours(24), 20, 6000, 2000, 10);

    private FakeChatModel model;
    private PostAssistService service;

    @BeforeEach
    void setUp() {
        model = new FakeChatModel();
        service = new PostAssistService(ChatClient.create(model), rateLimiter, properties);
        lenient().when(rateLimiter.tryAcquire(USER_ID)).thenReturn(true);
    }

    @Test
    void suggestTitles_blankContent_rejectsBeforeAnyInteraction() {
        assertThatThrownBy(() -> service.suggestTitles(USER_ID, new PostAssistRequest(null, null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.suggestTitles(USER_ID, new PostAssistRequest(null, "   ")))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void suggestTitles_contentOverByteLimit_rejectsBeforeAnyInteraction() {
        String tooLong = "a".repeat(60_001);

        assertThatThrownBy(() -> service.suggestTitles(USER_ID, new PostAssistRequest(null, tooLong)))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void suggestTitles_titleOver200Chars_rejectsBeforeAnyInteraction() {
        String tooLongTitle = "t".repeat(201);

        assertThatThrownBy(() ->
                service.suggestTitles(USER_ID, new PostAssistRequest(tooLongTitle, VALID_CONTENT)))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void suggestTitles_unconfigured_throwsUnavailableBeforeLimiter() {
        PostAssistService unconfigured = new PostAssistService(ChatClient.create(model), rateLimiter,
                new AssistantProperties("", Duration.ofHours(24), 20, 6000, 2000, 10));

        assertThatThrownBy(() ->
                unconfigured.suggestTitles(USER_ID, new PostAssistRequest(null, VALID_CONTENT)))
                .isInstanceOf(AssistantUnavailableException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void suggestTitles_rateLimited_throwsRateLimitBeforeModelCall() {
        when(rateLimiter.tryAcquire(USER_ID)).thenReturn(false);

        assertThatThrownBy(() ->
                service.suggestTitles(USER_ID, new PostAssistRequest(null, VALID_CONTENT)))
                .isInstanceOf(RateLimitExceededException.class);

        assertThat(model.prompts).isEmpty();
    }

    @Test
    void suggestTitles_sanitizesCandidatesAndSendsContentToTheModel() {
        model.replying("[\"  Chengdu in 3 days  \", \"\", \"   \", \"Chengdu in 3 days\", "
                + "\"Delicious Sichuan flavors\"]");

        TitleSuggestionsResponse response =
                service.suggestTitles(USER_ID, new PostAssistRequest("My trip", VALID_CONTENT));

        assertThat(response.suggestions())
                .containsExactly("Chengdu in 3 days", "Delicious Sichuan flavors");
        assertThat(model.prompts).hasSize(1);
        assertThat(model.prompts.get(0).getInstructions().get(0).getText())
                .isEqualTo(PostAssistService.TITLE_SYSTEM_PROMPT);
        assertThat(model.prompts.get(0).getInstructions().get(1).getText())
                .contains(VALID_CONTENT);
    }

    @Test
    void suggestTitles_blankAndOverlongEntriesDroppedWithoutRetry() {
        model.replying("[\"" + "x".repeat(201) + "\", \"\", \"Valid title\"]");

        TitleSuggestionsResponse response =
                service.suggestTitles(USER_ID, new PostAssistRequest(null, VALID_CONTENT));

        assertThat(response.suggestions()).containsExactly("Valid title");
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void suggestTitles_unparseableThenValid_retriesOnce() {
        model.replying("not json at all", "[\"Recovered title\"]");

        TitleSuggestionsResponse response =
                service.suggestTitles(USER_ID, new PostAssistRequest(null, VALID_CONTENT));

        assertThat(response.suggestions()).containsExactly("Recovered title");
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void suggestTitles_unparseableTwice_throwsSanitizedUnavailable() {
        model.replying("not json at all", "still not json");

        assertThatThrownBy(() ->
                service.suggestTitles(USER_ID, new PostAssistRequest(null, VALID_CONTENT)))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessage(SANITIZED_UNAVAILABLE);

        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void suggestTitles_fullyInvalidCandidates_retriesOnceThenUnavailable() {
        model.replying("[\"\", \"   \"]", "[\"\"]");

        assertThatThrownBy(() ->
                service.suggestTitles(USER_ID, new PostAssistRequest(null, VALID_CONTENT)))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessage(SANITIZED_UNAVAILABLE);

        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void suggestTitles_contentAtByteLimit_isAccepted() {
        model.replying("[\"Ok title\"]");

        TitleSuggestionsResponse response = service.suggestTitles(USER_ID,
                new PostAssistRequest(null, "a".repeat(60_000)));

        assertThat(response.suggestions()).containsExactly("Ok title");
    }

    @Test
    void suggestTags_blankContent_rejectsBeforeAnyInteraction() {
        assertThatThrownBy(() -> service.suggestTags(USER_ID, new PostAssistRequest(null, "   ")))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void suggestTags_dropsBlankCommaOverlongAndDuplicateEntries() {
        model.replying("[\"food\", \"food\", \"no, commas\", \"" + "x".repeat(51)
                + "\", \"\", \"history\"]");

        TagSuggestionsResponse response =
                service.suggestTags(USER_ID, new PostAssistRequest(null, VALID_CONTENT));

        assertThat(response.tags()).containsExactly("food", "history");
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void suggestTags_capsAtEightEntries() {
        model.replying("[\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\"]");

        TagSuggestionsResponse response =
                service.suggestTags(USER_ID, new PostAssistRequest(null, VALID_CONTENT));

        assertThat(response.tags()).containsExactly("a", "b", "c", "d", "e", "f", "g", "h");
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void polish_guardsRunBeforeAnyEmission() {
        assertThatThrownBy(() -> service.polish(USER_ID, new PostAssistRequest(null, "   ")))
                .isInstanceOf(InvalidRequestException.class);
        PostAssistService unconfigured = new PostAssistService(ChatClient.create(model), rateLimiter,
                new AssistantProperties("", Duration.ofHours(24), 20, 6000, 2000, 10));
        assertThatThrownBy(() ->
                unconfigured.polish(USER_ID, new PostAssistRequest(null, VALID_CONTENT)))
                .isInstanceOf(AssistantUnavailableException.class);

        assertThat(model.prompts).isEmpty();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void polish_rateLimited_throwsBeforeStreaming() {
        when(rateLimiter.tryAcquire(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.polish(USER_ID, new PostAssistRequest(null, VALID_CONTENT)))
                .isInstanceOf(RateLimitExceededException.class);

        assertThat(model.prompts).isEmpty();
    }

    @Test
    void polish_streamsDeltasInOrderThenDoneWithoutConversationHistory() {
        model.emitting("Better ", "", "text");

        List<ServerSentEvent<PostPolishEvent>> events =
                service.polish(USER_ID, new PostAssistRequest(null, VALID_CONTENT))
                        .collectList().block();

        assertThat(events).hasSize(3);
        assertThat(events.get(0).event()).isEqualTo("delta");
        assertThat(((PostPolishEvent.Delta) events.get(0).data()).text()).isEqualTo("Better ");
        assertThat(events.get(1).event()).isEqualTo("delta");
        assertThat(((PostPolishEvent.Delta) events.get(1).data()).text()).isEqualTo("text");
        assertThat(events.get(2).event()).isEqualTo("done");
        assertThat(events.get(2).data()).isInstanceOf(PostPolishEvent.Done.class);
        assertThat(model.prompts).hasSize(1);
        assertThat(model.prompts.get(0).getInstructions()).hasSize(2);
        assertThat(model.prompts.get(0).getInstructions().get(0).getText())
                .isEqualTo(PostAssistService.POLISH_SYSTEM_PROMPT);
        assertThat(model.prompts.get(0).getInstructions().get(1).getText())
                .contains(VALID_CONTENT);
    }

    @Test
    void polish_midStreamFailure_emitsSanitizedErrorFrame() {
        model.emitting("Partial")
                .thenFailingWith(new RuntimeException("upstream 500 secret detail"));

        List<ServerSentEvent<PostPolishEvent>> events =
                service.polish(USER_ID, new PostAssistRequest(null, VALID_CONTENT))
                        .collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).event()).isEqualTo("delta");
        assertThat(events.get(1).event()).isEqualTo("error");
        PostPolishEvent.Error error = (PostPolishEvent.Error) events.get(1).data();
        assertThat(error.message()).isEqualTo(SANITIZED_UNAVAILABLE);
        assertThat(error.message()).doesNotContain("secret").doesNotContain("500");
    }

    /** Hand-rolled model: records prompts, replays queued one-shot replies for {@code call()}, and emits stream chunks. */
    private static final class FakeChatModel implements ChatModel {

        private final List<Prompt> prompts = new ArrayList<>();
        private final Deque<String> callReplies = new ArrayDeque<>();
        private List<String> streamChunks = List.of();
        private Throwable streamError;

        FakeChatModel replying(String... texts) {
            callReplies.addAll(List.of(texts));
            return this;
        }

        FakeChatModel emitting(String... chunks) {
            this.streamChunks = List.of(chunks);
            return this;
        }

        FakeChatModel thenFailingWith(Throwable error) {
            this.streamError = error;
            return this;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            String text = callReplies.isEmpty() ? "[]" : callReplies.poll();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            prompts.add(prompt);
            Flux<ChatResponse> responses = Flux.fromIterable(streamChunks)
                    .map(text -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
            if (streamError != null) {
                return responses.concatWith(Flux.error(streamError));
            }
            return responses;
        }
    }
}
