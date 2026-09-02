package com.first.app.controller;

import com.first.app.dto.CommentResponse;
import com.first.app.dto.CreateCommentRequest;
import com.first.app.dto.PageResponse;
import com.first.app.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(@PathVariable Long postId,
                                                  @Valid @RequestBody CreateCommentRequest request,
                                                  HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createTopLevel(postId, request, userId));
    }

    @PostMapping("/comments/{parentId}/replies")
    public ResponseEntity<CommentResponse> reply(@PathVariable Long parentId,
                                                 @Valid @RequestBody CreateCommentRequest request,
                                                 HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.reply(parentId, request, userId));
    }

    @GetMapping("/posts/{postId}/comments")
    public PageResponse<CommentResponse> findTopLevel(@PathVariable Long postId,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return commentService.findTopLevel(postId, pageable);
    }

    @GetMapping("/comments/{parentId}/replies")
    public PageResponse<CommentResponse> findReplies(@PathVariable Long parentId,
                                                     @PageableDefault(size = 20) Pageable pageable) {
        return commentService.findReplies(parentId, pageable);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        commentService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
