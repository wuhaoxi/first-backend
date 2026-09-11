package com.first.app.service;

import com.first.app.dto.CreatePostRequest;
import com.first.app.dto.ImageUploadResponse;
import com.first.app.dto.PostListResponse;
import com.first.app.dto.PostSort;
import com.first.app.dto.PostSummary;
import com.first.app.dto.UpdatePostRequest;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.PostRepository;
import com.first.app.util.PostCursorCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostStatsEnricher postStatsEnricher;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_CONTENT_BYTES = 60_000; // headroom under MySQL TEXT limit (65,535 bytes)

    public Post create(CreatePostRequest request, Long userId) {
        List<String> tags = sanitizeTags(request.getTags());
        validateTags(tags);
        validateContentLength(request.getContent());

        PostStatus status = request.getStatus() != null ? request.getStatus() : PostStatus.DRAFT;

        Post post = Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .tags(tags)
                .status(status)
                .authorId(userId)
                .coverImage(request.getCoverImage())
                .build();

        return postRepository.save(post);
    }

    public List<Post> findPublishedList() {
        return postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);
    }

    public PostListResponse findList(String sort, Integer page, Integer size, String cursor) {
        PostSort resolvedSort = PostSort.from(sort);
        String resolvedCursor = (cursor == null || cursor.isBlank()) ? null : cursor;
        if (resolvedCursor != null && resolvedSort != PostSort.LATEST) {
            throw new InvalidRequestException("Cursor is only supported with sort=latest");
        }

        int resolvedSize = normalizeSize(size);
        if (resolvedCursor != null) {
            return findListByCursor(resolvedSize, resolvedCursor);
        }
        return findListByPage(resolvedSort, normalizePage(page), resolvedSize);
    }

    private PostListResponse findListByCursor(int size, String cursor) {
        PostCursorCodec.Cursor decoded = PostCursorCodec.decode(cursor);
        List<Post> fetched = postRepository.findLatestBeforeCursor(
                PostStatus.PUBLISHED, decoded.createdAt(), decoded.id(), PageRequest.of(0, size + 1));
        boolean hasMore = fetched.size() > size;
        List<Post> posts = hasMore ? fetched.subList(0, size) : fetched;
        long totalElements = postRepository.countByStatus(PostStatus.PUBLISHED);
        return buildResponse(posts, 0, size, totalElements, hasMore, PostSort.LATEST);
    }

    private PostListResponse findListByPage(PostSort sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (sort == PostSort.UPVOTES) {
            Page<Long> idPage = postRepository.findIdPageByUpvoteCount(PostStatus.PUBLISHED, pageable);
            return buildResponse(hydrateInOrder(idPage.getContent()), page, size,
                    idPage.getTotalElements(), page + 1 < idPage.getTotalPages(), sort);
        }
        Page<Post> postPage = sort == PostSort.COMMENTS
                ? postRepository.findByStatusOrderByCommentCountDescIdDesc(PostStatus.PUBLISHED, pageable)
                : postRepository.findByStatusOrderByCreatedAtDescIdDesc(PostStatus.PUBLISHED, pageable);
        return buildResponse(postPage.getContent(), page, size, postPage.getTotalElements(),
                page + 1 < postPage.getTotalPages(), sort);
    }

    private List<Post> hydrateInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> byId = postRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Post::getId, post -> post));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private PostListResponse buildResponse(List<Post> posts, int page, int size, long totalElements,
                                           boolean hasMore, PostSort sort) {
        List<PostSummary> content = posts.stream().map(PostSummary::from).toList();
        postStatsEnricher.enrich(content);

        String nextCursor = null;
        if (sort == PostSort.LATEST && hasMore && !posts.isEmpty()) {
            Post last = posts.get(posts.size() - 1);
            nextCursor = PostCursorCodec.encode(last.getCreatedAt(), last.getId());
        }

        return PostListResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages(totalElements, size))
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    private int normalizeSize(Integer size) {
        int resolved = size == null ? DEFAULT_PAGE_SIZE : size;
        if (resolved < 1) {
            throw new InvalidRequestException("Invalid size");
        }
        return Math.min(resolved, MAX_PAGE_SIZE);
    }

    private int normalizePage(Integer page) {
        int resolved = page == null ? 0 : page;
        if (resolved < 0) {
            throw new InvalidRequestException("Invalid page");
        }
        return resolved;
    }

    private static int totalPages(long totalElements, int size) {
        return (int) Math.ceil((double) totalElements / size);
    }

    public Post findById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));
    }

    public Post findByIdPublic(Long id) {
        Post post = findById(id);
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Post not found with id: " + id);
        }
        return post;
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
            validateContentLength(request.getContent());
            post.setContent(request.getContent());
        }
        if (request.getTags() != null) {
            List<String> tags = sanitizeTags(request.getTags());
            validateTags(tags);
            post.setTags(tags);
        }
        if (request.getStatus() != null) {
            post.setStatus(request.getStatus());
        }
        if (request.getCoverImage() != null) {
            post.setCoverImage(request.getCoverImage());
        }

        return postRepository.save(post);
    }

    public void delete(Long id, Long userId) {
        Post post = getPostAsAuthor(id, userId);
        postRepository.delete(post);
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

    private Post getPostAsAuthor(Long postId, Long userId) {
        Post post = findById(postId);
        if (!post.getAuthorId().equals(userId)) {
            throw new InvalidRequestException("You can only edit your own posts");
        }
        return post;
    }

    private void validateContentLength(String content) {
        if (content != null && content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new InvalidRequestException("content must not exceed " + MAX_CONTENT_BYTES + " bytes");
        }
    }

    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream()
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
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
            }
        }
    }
}
