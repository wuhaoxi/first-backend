package com.first.app.service;

import com.first.app.dto.AttractionCommentResponse;
import com.first.app.dto.CreateCommentRequest;
import com.first.app.dto.PageResponse;
import com.first.app.entity.AttractionComment;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.AttractionCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Comment operations for attractions. Mirrors {@link CommentService} except
 * that no stored counter is maintained: the attraction row is never written,
 * and {@code commentCount} is computed live by {@code AttractionInteractionEnricher}.
 */
@Service
@RequiredArgsConstructor
public class AttractionCommentService {

    private final AttractionCommentRepository attractionCommentRepository;
    private final AttractionService attractionService;

    @Transactional
    public AttractionCommentResponse createTopLevel(Long attractionId, CreateCommentRequest request, Long userId) {
        attractionService.findByIdPublic(attractionId);

        AttractionComment comment = attractionCommentRepository.save(AttractionComment.builder()
                .attractionId(attractionId)
                .userId(userId)
                .content(request.getContent())
                .build());

        return AttractionCommentResponse.from(comment);
    }

    @Transactional
    public AttractionCommentResponse reply(Long parentId, CreateCommentRequest request, Long userId) {
        AttractionComment parent = getVisibleComment(parentId);
        attractionService.findByIdPublic(parent.getAttractionId());

        AttractionComment reply = attractionCommentRepository.save(AttractionComment.builder()
                .attractionId(parent.getAttractionId())
                .userId(userId)
                .parentCommentId(parentId)
                .content(request.getContent())
                .build());

        return AttractionCommentResponse.from(reply);
    }

    public PageResponse<AttractionCommentResponse> findTopLevel(Long attractionId, Pageable pageable) {
        attractionService.findByIdPublic(attractionId);
        return PageResponse.from(attractionCommentRepository
                .findByAttractionIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(attractionId, pageable)
                .map(this::withReplyCount));
    }

    public PageResponse<AttractionCommentResponse> findReplies(Long parentId, Pageable pageable) {
        getVisibleComment(parentId);
        return PageResponse.from(attractionCommentRepository
                .findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(parentId, pageable)
                .map(this::withReplyCount));
    }

    private AttractionCommentResponse withReplyCount(AttractionComment comment) {
        AttractionCommentResponse response = AttractionCommentResponse.from(comment);
        response.setReplyCount(attractionCommentRepository.countByParentCommentIdAndDeletedFalse(comment.getId()));
        return response;
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        AttractionComment comment = getVisibleComment(commentId);
        if (!comment.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only delete your own comments");
        }

        List<AttractionComment> subtree = collectSubtree(comment);
        subtree.forEach(c -> c.setDeleted(true));
        attractionCommentRepository.saveAll(subtree);
    }

    private AttractionComment getVisibleComment(Long id) {
        AttractionComment comment = attractionCommentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + id));
        if (comment.isDeleted()) {
            throw new ResourceNotFoundException("Comment not found with id: " + id);
        }
        return comment;
    }

    private List<AttractionComment> collectSubtree(AttractionComment root) {
        List<AttractionComment> all = attractionCommentRepository
                .findByAttractionIdAndDeletedFalse(root.getAttractionId());

        Map<Long, List<AttractionComment>> childrenByParent = all.stream().collect(Collectors.groupingBy(
                c -> c.getParentCommentId() == null ? -1L : c.getParentCommentId()));

        List<AttractionComment> subtree = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(root.getId());
        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            for (AttractionComment child : childrenByParent.getOrDefault(currentId, List.of())) {
                subtree.add(child);
                queue.add(child.getId());
            }
        }
        subtree.add(0, root);
        return subtree;
    }
}
