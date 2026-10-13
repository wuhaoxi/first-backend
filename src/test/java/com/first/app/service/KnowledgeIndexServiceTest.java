package com.first.app.service;

import com.first.app.config.KnowledgeBaseProperties;
import com.first.app.dto.ReindexResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.exception.KnowledgeBaseUnavailableException;
import com.first.app.integration.KnowledgeVectorStore;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
import com.first.app.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeIndexServiceTest {

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
    void publishedPost_isIndexedWithChunkMetadata() {
        Post post = Post.builder()
                .id(5L)
                .title("西湖游记")
                .content("西湖的春天最美。")
                .status(PostStatus.PUBLISHED)
                .build();
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));

        service(CONFIGURED).syncPost(5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeVectorStore.Chunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).add(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).id()).isEqualTo("posts:5:0");
        assertThat(captor.getValue().get(0).metadata()).containsEntry("sourceType", "posts");
        verify(store, never()).deleteBySource(any(), anyLong());
    }

    @Test
    void draftPost_isRemovedFromTheIndex() {
        Post post = Post.builder()
                .id(5L)
                .title("草稿")
                .content("还未发布。")
                .status(PostStatus.DRAFT)
                .build();
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));

        service(CONFIGURED).syncPost(5L);

        verify(store).deleteBySource("posts", 5L);
        verify(store, never()).add(any());
    }

    @Test
    void missingPost_isRemovedFromTheIndex() {
        when(postRepository.findById(5L)).thenReturn(Optional.empty());

        service(CONFIGURED).syncPost(5L);

        verify(store).deleteBySource("posts", 5L);
        verify(store, never()).add(any());
    }

    @Test
    void storeFailureDuringSync_isSwallowed() {
        when(postRepository.findById(5L)).thenReturn(Optional.of(Post.builder()
                .id(5L)
                .title("西湖游记")
                .content("内容")
                .status(PostStatus.PUBLISHED)
                .build()));
        Mockito.doThrow(new KnowledgeBaseUnavailableException("store down"))
                .when(store)
                .add(any());

        assertThatCode(() -> service(CONFIGURED).syncPost(5L)).doesNotThrowAnyException();
    }

    @Test
    void reindexAll_resetsThenIndexesOnlyPublishedSourcesAndCounts() {
        when(postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED)).thenReturn(List.of(
                Post.builder().id(1L).title("一").content("内容一").status(PostStatus.PUBLISHED).build(),
                Post.builder().id(2L).title("二").content("内容二").status(PostStatus.PUBLISHED).build()));
        when(attractionRepository.findByStatus(AttractionStatus.PUBLISHED)).thenReturn(List.of(
                Attraction.builder().id(3L).name("West Lake").nameZh("西湖").city("杭州").citySlug("hangzhou")
                        .summary("简介").description("描述").status(AttractionStatus.PUBLISHED).build()));
        when(cityRepository.findByStatus(CityStatus.PUBLISHED)).thenReturn(List.of(
                City.builder().id(4L).name("Hangzhou").nameZh("杭州").description("人间天堂")
                        .status(CityStatus.PUBLISHED).build()));

        ReindexResponse response = service(CONFIGURED).reindexAll();

        InOrder inOrder = inOrder(store);
        inOrder.verify(store).reset();
        inOrder.verify(store, Mockito.atLeastOnce()).add(any());
        verify(postRepository).findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);
        verify(attractionRepository).findByStatus(AttractionStatus.PUBLISHED);
        verify(cityRepository).findByStatus(CityStatus.PUBLISHED);
        assertThat(response.posts()).isEqualTo(2);
        assertThat(response.attractions()).isEqualTo(1);
        assertThat(response.cities()).isEqualTo(1);
        assertThat(response.chunks()).isEqualTo(4);
    }

    @Test
    void reindexAll_whenStoreUnavailable_propagatesTheUnavailableException() {
        Mockito.doThrow(new KnowledgeBaseUnavailableException("store down")).when(store).reset();

        assertThatThrownBy(() -> service(CONFIGURED).reindexAll())
                .isInstanceOf(KnowledgeBaseUnavailableException.class);
    }

    @Test
    void blankKey_skipsSyncAndReindexWithoutStoreCalls() {
        KnowledgeIndexService keyless = service(KEYLESS);
        when(postRepository.findById(5L)).thenReturn(Optional.of(Post.builder()
                .id(5L)
                .title("西湖游记")
                .content("内容")
                .status(PostStatus.PUBLISHED)
                .build()));

        keyless.syncPost(5L);
        ReindexResponse response = keyless.reindexAll();

        verifyNoInteractions(store);
        assertThat(response.posts()).isZero();
        assertThat(response.attractions()).isZero();
        assertThat(response.cities()).isZero();
        assertThat(response.chunks()).isZero();
    }

    @Test
    void missingPost_deleteUsesTheRequestedId() {
        when(postRepository.findById(9L)).thenReturn(Optional.empty());

        service(CONFIGURED).syncPost(9L);

        verify(store).deleteBySource(eq("posts"), eq(9L));
    }
}
