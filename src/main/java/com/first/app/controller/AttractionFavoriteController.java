package com.first.app.controller;

import com.first.app.service.AttractionFavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttractionFavoriteController {

    private final AttractionFavoriteService attractionFavoriteService;

    @PostMapping("/attractions/{attractionId}/favorite")
    public ResponseEntity<Map<String, Boolean>> toggle(@PathVariable Long attractionId,
                                                       HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("favorited", attractionFavoriteService.toggle(attractionId, userId)));
    }
}
