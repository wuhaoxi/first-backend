package com.first.app.controller;

import com.first.app.dto.VoteRequest;
import com.first.app.dto.VoteStatsResponse;
import com.first.app.service.VoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @PostMapping("/posts/{postId}/votes")
    public ResponseEntity<VoteStatsResponse> vote(@PathVariable Long postId,
                                                  @Valid @RequestBody VoteRequest request,
                                                  HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(voteService.vote(postId, request.getVoteType(), userId));
    }

    @GetMapping("/posts/{postId}/vote-stats")
    public VoteStatsResponse getVoteStats(@PathVariable Long postId,
                                          HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return voteService.getVoteStats(postId, userId);
    }
}
