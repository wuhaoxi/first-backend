package com.first.app.service;

import com.first.app.dto.CreatePostRequest;
import com.first.app.dto.ImageUploadResponse;
import com.first.app.dto.UpdatePostRequest;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.entity.User;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.PostRepository;
import com.first.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB

    public Post create(CreatePostRequest request, Long userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<String> tags = sanitizeTags(request.getTags());
        validateTags(tags);

        PostStatus status = request.getStatus() != null ? request.getStatus() : PostStatus.DRAFT;

        Post post = Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .tags(tags)
                .status(status)
                .author(author)
                .build();

        return postRepository.save(post);
    }

    public List<Post> findPublishedList() {
        return postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);
    }

    public Post findById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));
    }

    public Post update(Long id, UpdatePostRequest request, Long userId) {
        Post post = getPostAsAuthor(id, userId);

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new InvalidRequestException("title must not be blank");
            }
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            if (request.getContent().isBlank()) {
                throw new InvalidRequestException("content must not be blank");
            }
            post.setContent(request.getContent());
        }
        if (request.getTags() != null) {
            List<String> tags = sanitizeTags(request.getTags());
            validateTags(tags);
            post.setTags(tags);
        }
        if (request.getStatus() != null) {
            validateStatusTransition(post.getStatus(), request.getStatus());
            post.setStatus(request.getStatus());
        }

        return postRepository.save(post);
    }

    public void archive(Long id, Long userId) {
        Post post = getPostAsAuthor(id, userId);
        post.setStatus(PostStatus.ARCHIVED);
        postRepository.save(post);
    }

    private Post getPostAsAuthor(Long postId, Long userId) {
        Post post = findById(postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw new AccessDeniedException("You can only edit your own posts");
        }
        return post;
    }

    private void validateStatusTransition(PostStatus from, PostStatus to) {
        if (to == PostStatus.DRAFT && (from == PostStatus.PUBLISHED || from == PostStatus.ARCHIVED)) {
            throw new InvalidRequestException("Cannot revert " + from + " to DRAFT");
        }
    }

    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream()
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }

    public ImageUploadResponse uploadImage(Long id, MultipartFile file, Long userId) {
        Post post = getPostAsAuthor(id, userId);

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("No image file provided");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new InvalidRequestException("Only JPEG and PNG images are allowed");
        }

        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new InvalidRequestException("Image must not exceed 5MB");
        }

        try {
            String extension = contentType.equals("image/jpeg") ? "jpg" : "png";
            Path uploadPath = Paths.get(uploadDir, "posts", id.toString());
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve("cover." + extension);
            file.transferTo(filePath.toFile());

            String url = "/api/uploads/posts/" + id + "/cover." + extension;
            post.setCoverImage(url);
            postRepository.save(post);

            return new ImageUploadResponse(url);
        } catch (IOException e) {
            throw new InvalidRequestException("Failed to upload image: " + e.getMessage());
        }
    }

    private void validateTags(List<String> tags) {
        if (tags != null) {
            if (tags.size() > 10) {
                throw new InvalidRequestException("tags must not exceed 10 items");
            }
            for (String tag : tags) {
                if (tag.isEmpty()) {
                    throw new InvalidRequestException("each tag must not be blank");
                }
                if (tag.length() > 50) {
                    throw new InvalidRequestException("each tag must not exceed 50 characters");
                }
                if (tag.contains(",")) {
                    throw new InvalidRequestException("tags must not contain commas");
                }
            }
        }
    }
}
