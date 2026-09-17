package com.first.app.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CityTest {

    @Test
    void shouldApplyDefaultValues() {
        City city = new City();

        assertThat(city.getStatus()).isEqualTo(CityStatus.DRAFT);
    }
}
