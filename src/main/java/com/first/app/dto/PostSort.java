package com.first.app.dto;

import com.first.app.exception.InvalidRequestException;

import java.util.Arrays;

public enum PostSort {

    LATEST("latest"),
    UPVOTES("upvotes"),
    COMMENTS("comments");

    private final String value;

    PostSort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static PostSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return LATEST;
        }
        String normalized = raw.trim();
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("Invalid sort: " + normalized));
    }
}
