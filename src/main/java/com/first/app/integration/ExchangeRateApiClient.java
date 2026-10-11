package com.first.app.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The only backend component that issues HTTP to ExchangeRate-API v6 (the keyed latest form).
 *
 * <p>The credential travels in the URL path and is bound from the process environment
 * ({@code ${EXCHANGE_RATE_API_KEY:}}) — it never appears in a tracked file, a response, or a log.
 * A missing key is an availability failure raised before any HTTP is attempted. Provider outcomes
 * are mapped here to {@link TravelProviderException}: {@code unsupported-code} is an input
 * rejection, every other error type (including a non-{@code success} {@code result} on HTTP 200)
 * is availability.
 */
@Component
public class ExchangeRateApiClient {

    private static final String PROVIDER = "ExchangeRate-API";

    private static final DateTimeFormatter PUBLISHED_AT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ExchangeRateApiClient(RestClient.Builder builder, TravelProviderProperties properties,
            ObjectMapper objectMapper, @Value("${EXCHANGE_RATE_API_KEY:}") String apiKey) {
        this.restClient = builder.baseUrl(properties.exchangeRateApiBaseUrl()).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    /**
     * Fetches the latest published rates for {@code base}; {@code quotes} is the normalized,
     * request-ordered currency list and drives the order of the returned entries.
     */
    public LatestRates fetchLatest(String base, List<String> quotes) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    "EXCHANGE_RATE_API_KEY is not set: the latest-rate form requires an ExchangeRate-API key");
        }

        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/v6/{key}/latest/{base}", apiKey, base)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw failureFrom(e, base);
        } catch (RestClientException e) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    PROVIDER + " returned a body that is not valid JSON");
        }
        if (body == null) {
            throw missingFields();
        }
        return assembleLatest(body, base, quotes);
    }

    private LatestRates assembleLatest(JsonNode body, String base, List<String> quotes) {
        // ExchangeRate-API reports API-level failures with `result: "error"` — and can do so on an
        // HTTP 200, so success cannot be assumed from the status alone.
        if ("error".equals(body.path("result").asText())) {
            String type = body.path("error-type").asText("unknown");
            TravelProviderException.Kind kind = "unsupported-code".equals(type)
                    ? TravelProviderException.Kind.INPUT_REJECTION
                    : TravelProviderException.Kind.UPSTREAM_UNAVAILABLE;
            throw new TravelProviderException(kind, PROVIDER + " returned error-type " + type);
        }

        String publishedAt = body.path("time_last_update_utc").asText(null);
        JsonNode conversionRates = body.path("conversion_rates");
        if (!"success".equals(body.path("result").asText()) || publishedAt == null
                || !conversionRates.isObject()) {
            throw missingFields();
        }

        List<Quote> assembled = new ArrayList<>();
        for (String currency : quotes) {
            JsonNode rate = conversionRates.get(currency);
            if (rate == null || !rate.isNumber()) {
                throw new TravelProviderException(TravelProviderException.Kind.INPUT_REJECTION,
                        PROVIDER + " does not support currency code " + currency);
            }
            assembled.add(new Quote(currency, rate.doubleValue()));
        }
        return new LatestRates(base, asOfDate(publishedAt), assembled);
    }

    private TravelProviderException failureFrom(RestClientResponseException e, String base) {
        JsonNode body = parseOrNull(e.getResponseBodyAsString());
        String type = body == null ? null : body.path("error-type").asText(null);
        String detail = body == null ? null : firstText(body, "message", "reason", "error-type");
        String suffix = detail == null ? "" : ": " + detail;
        String status = " responded with HTTP " + e.getStatusCode().value() + suffix;
        if ("unsupported-code".equals(type)) {
            // Name the code the caller sent so the error stays actionable end to end.
            return new TravelProviderException(TravelProviderException.Kind.INPUT_REJECTION,
                    PROVIDER + status + " (base code " + base + ")");
        }
        return new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                PROVIDER + status);
    }

    private String asOfDate(String timeLastUpdateUtc) {
        try {
            return OffsetDateTime.parse(timeLastUpdateUtc, PUBLISHED_AT).toLocalDate().toString();
        } catch (DateTimeParseException e) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    PROVIDER + " returned a malformed time_last_update_utc: " + timeLastUpdateUtc);
        }
    }

    private TravelProviderException missingFields() {
        return new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                PROVIDER + " response is missing expected fields (result, time_last_update_utc, conversion_rates)");
    }

    private JsonNode parseOrNull(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String firstText(JsonNode body, String... keys) {
        for (String key : keys) {
            JsonNode value = body.path(key);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    public record LatestRates(String base, String asOfDate, List<Quote> quotes) {
    }

    public record Quote(String currency, double rate) {
    }
}
