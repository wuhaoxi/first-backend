package com.first.app.service;

import com.first.app.dto.CommentResponse;
import com.first.app.dto.CreateCommentRequest;
import com.first.app.dto.PageResponse;
import com.first.app.entity.Comment;
import com.first.app.entity.Post;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.CommentRepository;
import com.first.app.repository.PostRepository;
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

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    @Transactional
    public CommentResponse createTopLevel(Long postId, CreateCommentRequest request, Long userId) {
        Post post = postService.findByIdPublic(postId);

        Comment comment = commentRepository.save(Comment.builder()
                .postId(postId)
                .userId(userId)
                .content(request.getContent())
                .build());

        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        return CommentResponse.from(comment);
    }

    @Transactional
    public CommentResponse reply(Long parentId, CreateCommentRequest request, Long userId) {
        Comment parent = getVisibleComment(parentId);
        Post post = postService.findByIdPublic(parent.getPostId());

        Comment reply = commentRepository.save(Comment.builder()
                .postId(parent.getPostId())
                .userId(userId)
                .parentCommentId(parentId)
                .content(request.getContent())
                .build());

        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        return CommentResponse.from(reply);
    }

    public PageResponse<CommentResponse> findTopLevel(Long postId, Pageable pageable) {
        postService.findByIdPublic(postId);
        return PageResponse.from(commentRepository
                .findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(postId, pageable)
                .map(CommentResponse::from));
    }

    public PageResponse<CommentResponse> findReplies(Long parentId, Pageable pageable) {
        getVisibleComment(parentId);
        return PageResponse.from(commentRepository
                .findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(parentId, pageable)
                .map(CommentResponse::from));
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = getVisibleComment(commentId);
        if (!comment.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only delete your own comments");
        }

        List<Comment> subtree = collectSubtree(comment);
        subtree.forEach(c -> c.setDeleted(true));
        commentRepository.saveAll(subtree);

        Post post = postRepository.findById(comment.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post not found with id: " + comment.getPostId()));
        post.setCommentCount(post.getCommentCount() - subtree.size());
        postRepository.save(post);
    }

    private Comment getVisibleComment(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + id));
        if (comment.isDeleted()) {
            throw new ResourceNotFoundException("Comment not found with id: " + id);
        }
        return comment;
    }

    private List<Comment> collectSubtree(Comment root) {
        List<Comment> all = commentRepository.findByPostIdAndDeletedFalse(root.getPostId());

        Map<Long, List<Comment>> childrenByParent = all.stream().collect(Collectors.groupingBy(
                c -> c.getParentCommentId() == null ? -1L : c.getParentCommentId()));

        List<Comment> subtree = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(root.getId());
        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            for (Comment child : childrenByParent.getOrDefault(currentId, List.of())) {
                subtree.add(child);
                queue.add(child.getId());
            }
        }
        subtree.add(0, root);
        return subtree;
    }
}
