package com.first.app.controller;

import com.first.app.dto.AttractionCommentResponse;
import com.first.app.dto.CreateCommentRequest;
import com.first.app.dto.PageResponse;
import com.first.app.service.AttractionCommentService;
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
public class AttractionCommentController {

    private final AttractionCommentService attractionCommentService;

    @PostMapping("/attractions/{attractionId}/comments")
    public ResponseEntity<AttractionCommentResponse> create(@PathVariable Long attractionId,
                                                            @Valid @RequestBody CreateCommentRequest request,
                                                            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attractionCommentService.createTopLevel(attractionId, request, userId));
    }

    @PostMapping("/attraction-comments/{parentId}/replies")
    public ResponseEntity<AttractionCommentResponse> reply(@PathVariable Long parentId,
                                                           @Valid @RequestBody CreateCommentRequest request,
                                                           HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attractionCommentService.reply(parentId, request, userId));
    }

    @GetMapping("/attractions/{attractionId}/comments")
    public PageResponse<AttractionCommentResponse> findTopLevel(@PathVariable Long attractionId,
                                                                @PageableDefault(size = 20) Pageable pageable) {
        return attractionCommentService.findTopLevel(attractionId, pageable);
    }

    @GetMapping("/attraction-comments/{parentId}/replies")
    public PageResponse<AttractionCommentResponse> findReplies(@PathVariable Long parentId,
                                                               @PageableDefault(size = 20) Pageable pageable) {
        return attractionCommentService.findReplies(parentId, pageable);
    }

    @DeleteMapping("/attraction-comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        attractionCommentService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
