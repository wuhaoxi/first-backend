package com.first.app.controller;

import com.first.app.dto.PageResponse;
import com.first.app.dto.PostSummary;
import com.first.app.service.BookmarkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/posts/{postId}/bookmark")
    public ResponseEntity<Map<String, Boolean>> toggle(@PathVariable Long postId,
                                                       HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("bookmarked", bookmarkService.toggle(postId, userId)));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<PageResponse<PostSummary>> list(HttpServletRequest httpRequest,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(bookmarkService.listBookmarks(userId, pageable));
    }
}
