package com.first.app.controller;

import com.first.app.dto.FeaturedGuideResponse;
import com.first.app.dto.HotPostResponse;
import com.first.app.dto.PopularCityResponse;
import com.first.app.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/featured-guides")
    public ResponseEntity<List<FeaturedGuideResponse>> getFeaturedGuides() {
        return ResponseEntity.ok(homeService.getFeaturedGuides());
    }

    @GetMapping("/popular-destinations")
    public ResponseEntity<List<PopularCityResponse>> getPopularDestinations() {
        return ResponseEntity.ok(homeService.getPopularDestinations());
    }

    @GetMapping("/hot-posts")
    public ResponseEntity<List<HotPostResponse>> getHotPosts() {
        return ResponseEntity.ok(homeService.getHotPosts());
    }
}
