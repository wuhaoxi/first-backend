package com.first.app.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The only backend component that issues HTTP to Frankfurter v2 (the series form).
 *
 * <p>Frankfurter publishes business days only: the returned series contains exactly the
 * provider's dates — nothing interpolated, nothing padded — re-sorted ascending. HTTP 422
 * is the provider's documented input-rejection status (unknown currency, conflicting
 * parameters); every other failure is availability-class.
 */
@Component
public class FrankfurterClient {

    private static final String PROVIDER = "Frankfurter";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FrankfurterClient(RestClient.Builder builder, TravelProviderProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(properties.frankfurterBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    /** Fetches the daily series for one pair over the closed interval; ascending by date. */
    public List<SeriesPoint> fetchSeries(String base, String quote, String fromDate, String toDate) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v2/rates")
                            .queryParam("base", base)
                            .queryParam("quotes", quote)
                            .queryParam("from", fromDate)
                            .queryParam("to", toDate)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw failureFrom(e);
        } catch (RestClientException e) {
            throw new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                    PROVIDER + " returned a body that is not valid JSON");
        }
        return assembleSeries(body);
    }

    private List<SeriesPoint> assembleSeries(JsonNode body) {
        if (body == null || !body.isArray()) {
            throw malformed();
        }
        List<SeriesPoint> series = new ArrayList<>();
        for (JsonNode entry : body) {
            JsonNode date = entry.path("date");
            JsonNode rate = entry.path("rate");
            if (!date.isTextual() || !rate.isNumber()) {
                throw malformed();
            }
            series.add(new SeriesPoint(date.asText(), rate.doubleValue()));
        }
        // ISO-8601 dates sort lexicographically; the provider order is not trusted.
        series.sort(Comparator.comparing(SeriesPoint::date));
        return series;
    }

    private TravelProviderException failureFrom(RestClientResponseException e) {
        JsonNode body = parseOrNull(e.getResponseBodyAsString());
        String detail = body == null ? null : firstText(body, "message", "reason", "error-type");
        String suffix = detail == null ? "" : ": " + detail;
        String status = " responded with HTTP " + e.getStatusCode().value() + suffix;
        TravelProviderException.Kind kind = e.getStatusCode().value() == 422
                ? TravelProviderException.Kind.INPUT_REJECTION
                : TravelProviderException.Kind.UPSTREAM_UNAVAILABLE;
        return new TravelProviderException(kind, PROVIDER + status);
    }

    private TravelProviderException malformed() {
        return new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                PROVIDER + " returned a malformed series response");
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

    public record SeriesPoint(String date, double rate) {
    }
}
