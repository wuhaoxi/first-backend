package com.first.app.config;

import com.first.app.integration.TravelProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestClientCustomizer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TravelProviderConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TravelProviderConfig.class);

    @Test
    void bindsTravelProviderPropertiesFromTheAppTravelProviderPrefix() {
        runner.withPropertyValues(
                        "app.travel.provider.open-meteo-base-url=https://api.open-meteo.com",
                        "app.travel.provider.frankfurter-base-url=https://api.frankfurter.dev",
                        "app.travel.provider.exchange-rate-api-base-url=https://v6.exchangerate-api.com",
                        "app.travel.provider.connect-timeout=3s",
                        "app.travel.provider.read-timeout=8s")
                .run(context -> {
                    TravelProviderProperties properties = context.getBean(TravelProviderProperties.class);
                    assertThat(properties.openMeteoBaseUrl()).isEqualTo("https://api.open-meteo.com");
                    assertThat(properties.frankfurterBaseUrl()).isEqualTo("https://api.frankfurter.dev");
                    assertThat(properties.exchangeRateApiBaseUrl()).isEqualTo("https://v6.exchangerate-api.com");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(8));
                });
    }

    @Test
    void missingTimeoutKeys_stillResolveToTheBoundedBudget() {
        runner.run(context -> {
            TravelProviderProperties properties = context.getBean(TravelProviderProperties.class);
            assertThat(properties.connectTimeout()).isLessThanOrEqualTo(Duration.ofSeconds(3));
            assertThat(properties.readTimeout()).isLessThanOrEqualTo(Duration.ofSeconds(8));
        });
    }

    @Test
    void registersTheTimeoutCustomizer() {
        runner.run(context -> assertThat(context.getBeansOfType(RestClientCustomizer.class)).hasSize(1));
    }
}
