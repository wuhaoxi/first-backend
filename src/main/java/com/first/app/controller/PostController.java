package com.first.app.controller;

import com.first.app.dto.CreatePostRequest;
import com.first.app.dto.ImageUploadResponse;
import com.first.app.dto.PostListResponse;
import com.first.app.dto.PostResponse;
import com.first.app.dto.UpdatePostRequest;
import com.first.app.entity.Post;
import com.first.app.repository.BookmarkRepository;
import com.first.app.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final BookmarkRepository bookmarkRepository;

    @PostMapping
    public ResponseEntity<PostResponse> create(@Valid @RequestBody CreatePostRequest request,
                                                HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Post post = postService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(post));
    }

    @GetMapping
    public PostListResponse findAll(@RequestParam(required = false) String sort,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String cursor) {
        return postService.findList(sort, page, size, cursor);
    }

    @GetMapping("/{id}")
    public PostResponse findById(@PathVariable Long id,
                                  HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Post post;
        if (userId != null && userId.equals(postService.findById(id).getAuthorId())) {
            post = postService.findById(id);
        } else {
            post = postService.findByIdPublic(id);
        }
        PostResponse response = PostResponse.from(post);
        if (userId != null) {
            response.setBookmarked(bookmarkRepository.findByPostIdAndUserId(id, userId).isPresent());
        }
        return response;
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdatePostRequest request,
                                                HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Post post = postService.update(id, request, userId);
        return ResponseEntity.ok(PostResponse.from(post));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        postService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<ImageUploadResponse> uploadImage(@PathVariable Long id,
                                                            @RequestParam("file") MultipartFile file,
                                                            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(postService.uploadImage(id, file, userId));
    }
}
