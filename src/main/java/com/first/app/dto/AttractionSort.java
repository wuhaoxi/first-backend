package com.first.app.dto;

import com.first.app.exception.InvalidRequestException;

import java.util.Arrays;

public enum AttractionSort {

    LATEST("latest"),
    POPULAR("popular"),
    RATING("rating"),
    HEAT("heat"),
    FAVORITES("favorites");

    private final String value;

    AttractionSort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AttractionSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return POPULAR;
        }
        String normalized = raw.trim();
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("Invalid sort: " + normalized));
    }
}
