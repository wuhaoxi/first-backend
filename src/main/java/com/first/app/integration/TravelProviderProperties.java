package com.first.app.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Provider configuration bound from {@code app.travel.provider.*} in the active profile.
 *
 * <p>The base URLs are profile-owned (dev/test YAML). The timeout budget is bounded even when a
 * profile omits it: provider calls get connect ≤ 3 s / read ≤ 8 s, attempted exactly once.
 */
@ConfigurationProperties(prefix = "app.travel.provider")
public record TravelProviderProperties(
        String openMeteoBaseUrl,
        String frankfurterBaseUrl,
        String exchangeRateApiBaseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public TravelProviderProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(8);
        }
    }
}
