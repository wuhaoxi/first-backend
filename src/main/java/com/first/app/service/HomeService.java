package com.first.app.service;

import com.first.app.dto.FeaturedGuideResponse;
import com.first.app.dto.HotPostResponse;
import com.first.app.dto.PopularCityResponse;
import com.first.app.entity.Post;
import com.first.app.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final PostRepository postRepository;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public List<FeaturedGuideResponse> getFeaturedGuides() {
        return Collections.emptyList();
    }

    public List<PopularCityResponse> getPopularDestinations() {
        return Collections.emptyList();
    }

    public List<HotPostResponse> getHotPosts() {
        List<Post> publishedPosts = postRepository.findByStatusOrderByCreatedAtDesc(
                com.first.app.entity.PostStatus.PUBLISHED);

        return publishedPosts.stream()
                .sorted(Comparator
                        .comparingInt((Post p) -> p.getCommentCount() > 0 ? 0 : 1)
                        .thenComparing(Post::getCreatedAt, Comparator.reverseOrder()))
                .limit(5)
                .map(this::toHotPostResponse)
                .collect(Collectors.toList());
    }

    private HotPostResponse toHotPostResponse(Post post) {
        return new HotPostResponse(
                post.getId(),
                post.getTitle(),
                null, // cityName — not yet supported on Post entity
                post.getCommentCount(),
                post.getCreatedAt() != null
                        ? post.getCreatedAt().format(ISO_FORMATTER)
                        : null
        );
    }
}
