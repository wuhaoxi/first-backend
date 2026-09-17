package com.first.app.service;

import com.first.app.dto.LinkedAttractionResponse;
import com.first.app.dto.PostResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.PostAttraction;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.PostAttractionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Enriches a single {@link PostResponse} with its linked published attractions,
 * in junction creation order.
 *
 * <p>At most two repository queries: the junction rows for the post, then one
 * batched {@code findByIdInAndStatus} fetch for the linked attractions. DRAFT
 * or vanished attractions are silently filtered out; the list is empty when
 * there are no links.</p>
 */
@Service
public class PostAttractionEnricher {

    private final PostAttractionRepository postAttractionRepository;
    private final AttractionRepository attractionRepository;

    public PostAttractionEnricher(PostAttractionRepository postAttractionRepository,
                                  AttractionRepository attractionRepository) {
        this.postAttractionRepository = postAttractionRepository;
        this.attractionRepository = attractionRepository;
    }

    public void enrich(PostResponse response) {
        if (response == null || response.getId() == null) {
            return;
        }
        List<PostAttraction> links = postAttractionRepository.findByPostIdOrderByIdAsc(response.getId());
        if (links.isEmpty()) {
            response.setAttractions(List.of());
            return;
        }
        List<Long> attractionIds = links.stream()
                .map(PostAttraction::getAttractionId)
                .toList();
        Map<Long, Attraction> byId = attractionRepository
                .findByIdInAndStatus(attractionIds, AttractionStatus.PUBLISHED)
                .stream()
                .collect(Collectors.toMap(Attraction::getId, attraction -> attraction));
        response.setAttractions(links.stream()
                .map(link -> byId.get(link.getAttractionId()))
                .filter(Objects::nonNull)
                .map(LinkedAttractionResponse::from)
                .toList());
    }
}
