package com.first.app.service;

import com.first.app.dto.CommentResponse;
import com.first.app.dto.CreateCommentRequest;
import com.first.app.dto.PageResponse;
import com.first.app.entity.Comment;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.CommentRepository;
import com.first.app.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostService postService;

    @InjectMocks
    private CommentService commentService;

    private static final Long POST_ID = 1L;
    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private Post buildPost(Long id, int commentCount) {
        return Post.builder()
                .id(id).title("Test Post").content("# Hello")
                .status(PostStatus.PUBLISHED).authorId(AUTHOR_ID)
                .commentCount(commentCount)
                .build();
    }

    private Comment buildComment(Long id, Long postId, Long userId, Long parentId, String content) {
        return Comment.builder()
                .id(id).postId(postId).userId(userId)
                .parentCommentId(parentId).content(content)
                .build();
    }

    private CreateCommentRequest buildRequest(String content) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        return request;
    }

    @Test
    void createTopLevel_shouldSaveCommentAndIncrementCount() {
        Post post = buildPost(POST_ID, 3);
        when(postService.findByIdPublic(POST_ID)).thenReturn(post);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        CommentResponse response = commentService.createTopLevel(
                POST_ID, buildRequest("Great post"), AUTHOR_ID);

        assertThat(response.getPostId()).isEqualTo(POST_ID);
        assertThat(response.getUserId()).isEqualTo(AUTHOR_ID);
        assertThat(response.getContent()).isEqualTo("Great post");
        assertThat(response.getParentCommentId()).isNull();

        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getParentCommentId()).isNull();

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getCommentCount()).isEqualTo(4);
    }

    @Test
    void createTopLevel_shouldThrow404WhenPostNotPublished() {
        when(postService.findByIdPublic(POST_ID))
                .thenThrow(new ResourceNotFoundException("Post not found with id: " + POST_ID));

        assertThatThrownBy(() -> commentService.createTopLevel(
                POST_ID, buildRequest("hello"), AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reply_shouldInheritPostIdAndSetParent() {
        Comment parent = buildComment(5L, POST_ID, OTHER_USER_ID, null, "parent");
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPost(POST_ID, 1));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        CommentResponse response = commentService.reply(5L, buildRequest("agreed"), AUTHOR_ID);

        assertThat(response.getParentCommentId()).isEqualTo(5L);
        assertThat(response.getPostId()).isEqualTo(POST_ID);
        assertThat(response.getUserId()).isEqualTo(AUTHOR_ID);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(POST_ID);
        assertThat(captor.getValue().getParentCommentId()).isEqualTo(5L);
    }

    @Test
    void reply_shouldThrow404WhenParentMissing() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.reply(99L, buildRequest("hi"), AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reply_shouldThrow404WhenParentDeleted() {
        Comment parent = buildComment(5L, POST_ID, OTHER_USER_ID, null, "parent");
        parent.setDeleted(true);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.reply(5L, buildRequest("hi"), AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reply_shouldThrow404WhenPostNotPublished() {
        Comment parent = buildComment(5L, POST_ID, OTHER_USER_ID, null, "parent");
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(postService.findByIdPublic(POST_ID))
                .thenThrow(new ResourceNotFoundException("Post not found with id: " + POST_ID));

        assertThatThrownBy(() -> commentService.reply(5L, buildRequest("hi"), AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findTopLevel_shouldReturnPageResponse() {
        when(postService.findByIdPublic(POST_ID)).thenReturn(buildPost(POST_ID, 2));
        Page<Comment> page = new PageImpl<>(
                List.of(buildComment(1L, POST_ID, AUTHOR_ID, null, "c1"),
                        buildComment(2L, POST_ID, OTHER_USER_ID, null, "c2")),
                PageRequest.of(0, 20), 2);
        when(commentRepository.findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(
                POST_ID, PageRequest.of(0, 20))).thenReturn(page);

        PageResponse<CommentResponse> result = commentService.findTopLevel(POST_ID, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("c1");
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    void findReplies_shouldThrow404WhenParentMissingOrDeleted() {
        when(commentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.findReplies(5L, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_shouldCascadeSubtreeAndDecrementCount() {
        // 3-level tree: top(1) -> reply(2) -> nested(3)
        Comment top = buildComment(1L, POST_ID, AUTHOR_ID, null, "top");
        Comment reply = buildComment(2L, POST_ID, OTHER_USER_ID, 1L, "reply");
        Comment nested = buildComment(3L, POST_ID, AUTHOR_ID, 2L, "nested");
        Post post = buildPost(POST_ID, 3);

        when(commentRepository.findById(1L)).thenReturn(Optional.of(top));
        when(commentRepository.findByPostIdAndDeletedFalse(POST_ID))
                .thenReturn(List.of(top, reply, nested));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        commentService.delete(1L, AUTHOR_ID);

        verify(commentRepository).saveAll(List.of(top, reply, nested));
        assertThat(top.isDeleted()).isTrue();
        assertThat(reply.isDeleted()).isTrue();
        assertThat(nested.isDeleted()).isTrue();
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getCommentCount()).isZero();
    }

    @Test
    void delete_shouldThrow403WhenNotAuthor() {
        Comment comment = buildComment(1L, POST_ID, OTHER_USER_ID, null, "not mine");
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(1L, AUTHOR_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_shouldThrow404WhenAlreadyDeleted() {
        Comment comment = buildComment(1L, POST_ID, AUTHOR_ID, null, "already gone");
        comment.setDeleted(true);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(1L, AUTHOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
