package com.first.app.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttractionTest {

    @Test
    void shouldApplyDefaultValues() {
        Attraction attraction = new Attraction();

        assertThat(attraction.getStatus()).isEqualTo(AttractionStatus.DRAFT);
        assertThat(attraction.isPopular()).isFalse();
        assertThat(attraction.isBookingRequired()).isFalse();
        assertThat(attraction.getTags()).isEmpty();
    }
}
