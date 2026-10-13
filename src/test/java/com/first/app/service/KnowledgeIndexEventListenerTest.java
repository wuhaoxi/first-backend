package com.first.app.service;

import com.first.app.config.KnowledgeBaseProperties;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.exception.KnowledgeBaseUnavailableException;
import com.first.app.integration.KnowledgeVectorStore;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
import com.first.app.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeIndexEventListenerTest {

    private static final KnowledgeBaseProperties CONFIGURED =
            new KnowledgeBaseProperties("test-key", null, null, 0, 0, 0, 0, null, null);

    private static final KnowledgeBaseProperties KEYLESS =
            new KnowledgeBaseProperties(null, null, null, 0, 0, 0, 0, null, null);

    private final KnowledgeVectorStore store = mock(KnowledgeVectorStore.class);

    private final PostRepository postRepository = mock(PostRepository.class);

    private final AttractionRepository attractionRepository = mock(AttractionRepository.class);

    private final CityRepository cityRepository = mock(CityRepository.class);

    private final KnowledgeChunker chunker = new KnowledgeChunker(CONFIGURED);

    private KnowledgeIndexService service(KnowledgeBaseProperties properties) {
        return new KnowledgeIndexService(store, properties, chunker, postRepository, attractionRepository,
                cityRepository);
    }

    @Test
    void onPostChanged_isAnAfterCommitTransactionalEventListener() throws NoSuchMethodException {
        TransactionalEventListener annotation = KnowledgeIndexService.class
                .getMethod("onPostChanged", PostChangedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void publishedPost_isIndexed() {
        when(postRepository.findById(5L)).thenReturn(Optional.of(Post.builder()
                .id(5L)
                .title("西湖游记")
                .content("西湖的春天最美。")
                .status(PostStatus.PUBLISHED)
                .build()));

        service(CONFIGURED).onPostChanged(new PostChangedEvent(5L));

        verify(store).add(any());
        verify(store, never()).deleteBySource(any(), any(Long.class));
    }

    @Test
    void draftPost_isRemovedFromTheIndex() {
        when(postRepository.findById(5L)).thenReturn(Optional.of(Post.builder()
                .id(5L)
                .title("草稿")
                .content("还未发布。")
                .status(PostStatus.DRAFT)
                .build()));

        service(CONFIGURED).onPostChanged(new PostChangedEvent(5L));

        verify(store).deleteBySource("posts", 5L);
        verify(store, never()).add(any());
    }

    @Test
    void deletedPost_absentOnReload_isRemovedFromTheIndex() {
        when(postRepository.findById(5L)).thenReturn(Optional.empty());

        service(CONFIGURED).onPostChanged(new PostChangedEvent(5L));

        verify(store).deleteBySource("posts", 5L);
        verify(store, never()).add(any());
    }

    @Test
    void storeFailure_isSwallowedWithoutThrowing() {
        when(postRepository.findById(5L)).thenReturn(Optional.of(Post.builder()
                .id(5L)
                .title("西湖游记")
                .content("内容")
                .status(PostStatus.PUBLISHED)
                .build()));
        Mockito.doThrow(new KnowledgeBaseUnavailableException("store down"))
                .when(store)
                .add(any());

        assertThatCode(() -> service(CONFIGURED).onPostChanged(new PostChangedEvent(5L)))
                .doesNotThrowAnyException();
    }

    @Test
    void blankKey_isANoOp() {
        service(KEYLESS).onPostChanged(new PostChangedEvent(5L));

        verifyNoInteractions(store, postRepository, attractionRepository, cityRepository);
    }
}
