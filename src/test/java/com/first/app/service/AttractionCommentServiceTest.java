package com.first.app.service;

import com.first.app.dto.AttractionCommentResponse;
import com.first.app.dto.CreateCommentRequest;
import com.first.app.dto.PageResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionComment;
import com.first.app.entity.AttractionStatus;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.AttractionCommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttractionCommentServiceTest {

    @Mock
    private AttractionCommentRepository attractionCommentRepository;

    @Mock
    private AttractionService attractionService;

    @InjectMocks
    private AttractionCommentService attractionCommentService;

    private static final Long ATTRACTION_ID = 1L;
    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private Attraction buildAttraction(Long id) {
        Attraction attraction = Attraction.builder()
                .slug("forbidden-city").name("Forbidden City")
                .status(AttractionStatus.PUBLISHED)
                .build();
        attraction.setId(id);
        return attraction;
    }

    private AttractionComment buildComment(Long id, Long attractionId, Long userId, Long parentId, String content) {
        return AttractionComment.builder()
                .id(id).attractionId(attractionId).userId(userId)
                .parentCommentId(parentId).content(content)
                .build();
    }

    private CreateCommentRequest buildRequest(String content) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        return request;
    }

    @Test
    void createTopLevel_shouldSaveCommentWithoutTouchingAttractionRow() {
        when(attractionService.findByIdPublic(ATTRACTION_ID)).thenReturn(buildAttraction(ATTRACTION_ID));
        when(attractionCommentRepository.save(any(AttractionComment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AttractionCommentResponse response = attractionCommentService.createTopLevel(
                ATTRACTION_ID, buildRequest("Beautiful palace"), AUTHOR_ID);

        assertThat(response.getAttractionId()).isEqualTo(ATTRACTION_ID);
        assertThat(response.getUserId()).isEqualTo(AUTHOR_ID);
        assertThat(response.getContent()).isEqualTo("Beautiful palace");
        assertThat(response.getParentCommentId()).isNull();
        assertThat(response.getReplyCount()).isZero();

        ArgumentCaptor<AttractionComment> captor = ArgumentCaptor.forClass(AttractionComment.class);
        verify(attractionCommentRepository).save(captor.capture());
        assertThat(captor.getValue().getAttractionId()).isEqualTo(ATTRACTION_ID);
        assertThat(captor.getValue().getParentCommentId()).isNull();

        // No counter maintenance: the attraction is only validated, never saved.
        verify(attractionService).findByIdPublic(ATTRACTION_ID);
        verifyNoMoreInteractions(attractionService);
    }

    @Test
    void createTopLevel_shouldThrow404WhenAttractionNotPublished() {
        when(attractionService.findByIdPublic(ATTRACTION_ID))
                .thenThrow(new ResourceNotFoundException("Attraction not found with id: " + ATTRACTION_ID));

        assertThatThrownBy(() -> attractionCommentService.createTopLevel(
                ATTRACTION_ID, buildRequest("hello"), AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(attractionCommentRepository);
    }

    @Test
    void reply_shouldInheritAttractionIdAndSetParent() {
        AttractionComment parent = buildComment(5L, ATTRACTION_ID, OTHER_USER_ID, null, "parent");
        when(attractionCommentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(attractionService.findByIdPublic(ATTRACTION_ID)).thenReturn(buildAttraction(ATTRACTION_ID));
        when(attractionCommentRepository.save(any(AttractionComment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AttractionCommentResponse response = attractionCommentService.reply(5L, buildRequest("agreed"), AUTHOR_ID);

        assertThat(response.getParentCommentId()).isEqualTo(5L);
        assertThat(response.getAttractionId()).isEqualTo(ATTRACTION_ID);
        assertThat(response.getUserId()).isEqualTo(AUTHOR_ID);

        ArgumentCaptor<AttractionComment> captor = ArgumentCaptor.forClass(AttractionComment.class);
        verify(attractionCommentRepository).save(captor.capture());
        assertThat(captor.getValue().getAttractionId()).isEqualTo(ATTRACTION_ID);
        assertThat(captor.getValue().getParentCommentId()).isEqualTo(5L);

        verify(attractionService).findByIdPublic(ATTRACTION_ID);
        verifyNoMoreInteractions(attractionService);
    }

    @Test
    void reply_shouldThrow404WhenParentMissing() {
        when(attractionCommentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attractionCommentService.reply(99L, buildRequest("hi"), AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reply_shouldThrow404WhenParentDeleted() {
        AttractionComment parent = buildComment(5L, ATTRACTION_ID, OTHER_USER_ID, null, "parent");
        parent.setDeleted(true);
        when(attractionCommentRepository.findById(5L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> attractionCommentService.reply(5L, buildRequest("hi"), AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reply_shouldThrow404WhenAttractionNotPublished() {
        AttractionComment parent = buildComment(5L, ATTRACTION_ID, OTHER_USER_ID, null, "parent");
        when(attractionCommentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(attractionService.findByIdPublic(ATTRACTION_ID))
                .thenThrow(new ResourceNotFoundException("Attraction not found with id: " + ATTRACTION_ID));

        assertThatThrownBy(() -> attractionCommentService.reply(5L, buildRequest("hi"), AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(attractionCommentRepository, org.mockito.Mockito.never()).save(any(AttractionComment.class));
    }

    @Test
    void findTopLevel_shouldReturnPageResponseWithReplyCount() {
        when(attractionService.findByIdPublic(ATTRACTION_ID)).thenReturn(buildAttraction(ATTRACTION_ID));
        Page<AttractionComment> page = new PageImpl<>(
                List.of(buildComment(1L, ATTRACTION_ID, AUTHOR_ID, null, "c1"),
                        buildComment(2L, ATTRACTION_ID, OTHER_USER_ID, null, "c2")),
                PageRequest.of(0, 20), 2);
        when(attractionCommentRepository
                .findByAttractionIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(
                        ATTRACTION_ID, PageRequest.of(0, 20))).thenReturn(page);
        when(attractionCommentRepository.countByParentCommentIdAndDeletedFalse(1L)).thenReturn(3L);
        when(attractionCommentRepository.countByParentCommentIdAndDeletedFalse(2L)).thenReturn(0L);

        PageResponse<AttractionCommentResponse> result = attractionCommentService.findTopLevel(
                ATTRACTION_ID, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("c1");
        assertThat(result.getContent().get(0).getReplyCount()).isEqualTo(3);
        assertThat(result.getContent().get(1).getReplyCount()).isZero();
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    void findReplies_shouldPopulateReplyCount() {
        when(attractionCommentRepository.findById(5L))
                .thenReturn(Optional.of(buildComment(5L, ATTRACTION_ID, AUTHOR_ID, null, "parent")));
        Page<AttractionComment> page = new PageImpl<>(
                List.of(buildComment(2L, ATTRACTION_ID, OTHER_USER_ID, 5L, "r1")),
                PageRequest.of(0, 20), 1);
        when(attractionCommentRepository.findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(
                5L, PageRequest.of(0, 20))).thenReturn(page);
        when(attractionCommentRepository.countByParentCommentIdAndDeletedFalse(2L)).thenReturn(1L);

        PageResponse<AttractionCommentResponse> result = attractionCommentService.findReplies(
                5L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReplyCount()).isEqualTo(1);
        verify(attractionCommentRepository).countByParentCommentIdAndDeletedFalse(2L);
    }

    @Test
    void findReplies_shouldThrow404WhenParentMissingOrDeleted() {
        when(attractionCommentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attractionCommentService.findReplies(5L, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_shouldCascadeSubtreeWithoutCounterUpdates() {
        // 3-level tree: top(1) -> reply(2) -> nested(3)
        AttractionComment top = buildComment(1L, ATTRACTION_ID, AUTHOR_ID, null, "top");
        AttractionComment reply = buildComment(2L, ATTRACTION_ID, OTHER_USER_ID, 1L, "reply");
        AttractionComment nested = buildComment(3L, ATTRACTION_ID, AUTHOR_ID, 2L, "nested");

        when(attractionCommentRepository.findById(1L)).thenReturn(Optional.of(top));
        when(attractionCommentRepository.findByAttractionIdAndDeletedFalse(ATTRACTION_ID))
                .thenReturn(List.of(top, reply, nested));

        attractionCommentService.delete(1L, AUTHOR_ID);

        verify(attractionCommentRepository).saveAll(List.of(top, reply, nested));
        assertThat(top.isDeleted()).isTrue();
        assertThat(reply.isDeleted()).isTrue();
        assertThat(nested.isDeleted()).isTrue();
        // No counter maintenance means the attraction row is never touched on delete.
        verifyNoInteractions(attractionService);
    }

    @Test
    void delete_shouldThrow403WhenNotAuthor() {
        AttractionComment comment = buildComment(1L, ATTRACTION_ID, OTHER_USER_ID, null, "not mine");
        when(attractionCommentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> attractionCommentService.delete(1L, AUTHOR_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_shouldThrow404WhenAlreadyDeleted() {
        AttractionComment comment = buildComment(1L, ATTRACTION_ID, AUTHOR_ID, null, "already gone");
        comment.setDeleted(true);
        when(attractionCommentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> attractionCommentService.delete(1L, AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
