package com.first.app.config;

import com.first.app.integration.TravelProviderProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Registers the travel provider configuration and applies the bounded timeout budget to every
 * {@code RestClient} built from the auto-configured builder (the three provider clients are the
 * only RestClients in the app). The clients themselves never set a request factory, which keeps
 * them bindable to {@code MockRestServiceServer} in tests.
 */
@Configuration
@EnableConfigurationProperties(TravelProviderProperties.class)
public class TravelProviderConfig {

    @Bean
    public RestClientCustomizer travelProviderTimeoutCustomizer(TravelProviderProperties properties) {
        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(properties.readTimeout()));
        return builder -> builder.requestFactory(factory);
    }
}
