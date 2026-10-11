package com.first.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.first.app.dto.ExchangeRateHistoryResponse;
import com.first.app.dto.ExchangeRateLatestResponse;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.UpstreamUnavailableException;
import com.first.app.integration.ExchangeRateApiClient;
import com.first.app.integration.FrankfurterClient;
import com.first.app.integration.TravelProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The single rate implementation behind the REST endpoints and the MCP {@code get_exchange_rate}
 * tool: it normalizes and validates caller input, routes the latest form to
 * {@link ExchangeRateApiClient} and the series form to {@link FrankfurterClient}, serves Redis
 * through the shared generic cache-aside helper, and maps provider outcomes to domain
 * exceptions — input rejections to {@link InvalidRequestException}, availability failures to
 * {@link UpstreamUnavailableException}.
 *
 * <p>The latest cache key carries the alphabetically sorted quote list, so reordered requests
 * share one entry; the payload is re-projected into the caller's request order after every read.
 * Both forms cache for 24 hours — every provider republishes at most once per day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final String DEFAULT_BASE = "CNY";
    private static final List<String> DEFAULT_QUOTES = List.of("USD", "EUR", "JPY", "KRW");
    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");
    private static final int MAX_SPAN_DAYS = 366;

    private static final TypeReference<ExchangeRateLatestResponse> LATEST_TYPE =
            new TypeReference<>() {
            };
    private static final TypeReference<ExchangeRateHistoryResponse> SERIES_TYPE =
            new TypeReference<>() {
            };

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final ExchangeRateApiClient exchangeRateApiClient;
    private final FrankfurterClient frankfurterClient;
    private final RankingCacheService cache;

    public ExchangeRateLatestResponse getLatest(String baseParam, String quotesParam) {
        String base = normalizeBase(baseParam);
        List<String> quotes = normalizeQuotes(quotesParam);
        String key = "travel:rates:latest:" + base + ":" + String.join(",", sortedCopy(quotes));
        try {
            ExchangeRateLatestResponse payload = cache.getOrLoad(key, CACHE_TTL, LATEST_TYPE,
                    () -> ExchangeRateLatestResponse
                            .from(exchangeRateApiClient.fetchLatest(base, quotes)));
            return payload.withQuotesInOrder(quotes);
        } catch (TravelProviderException e) {
            throw translate(e, key);
        }
    }

    public ExchangeRateHistoryResponse getHistory(String baseParam, String quoteParam,
            String fromParam, String toParam) {
        String base = normalizeBase(baseParam);
        String quote = requireCode(quoteParam, "quote");
        LocalDate from = requireDate(fromParam, "from");
        LocalDate to = requireDate(toParam, "to");
        validateRange(from, to);
        String key = "travel:rates:series:" + base + ":" + quote + ":" + from + ":" + to;
        try {
            return cache.getOrLoad(key, CACHE_TTL, SERIES_TYPE,
                    () -> ExchangeRateHistoryResponse.from(base, quote, from.toString(),
                            to.toString(),
                            frankfurterClient.fetchSeries(base, quote, from.toString(),
                                    to.toString())));
        } catch (TravelProviderException e) {
            throw translate(e, key);
        }
    }

    private RuntimeException translate(TravelProviderException e, String key) {
        log.warn("Rate provider failure for {}: {}", key, e.reason());
        if (e.kind() == TravelProviderException.Kind.INPUT_REJECTION) {
            return new InvalidRequestException(e.reason());
        }
        return new UpstreamUnavailableException(e.reason());
    }

    private String normalizeBase(String baseParam) {
        if (baseParam == null || baseParam.isBlank()) {
            return DEFAULT_BASE;
        }
        String base = baseParam.trim().toUpperCase(Locale.ROOT);
        if (!ISO_4217.matcher(base).matches()) {
            throw new InvalidRequestException(
                    "base must be a 3-letter ISO-4217 code (got \"" + baseParam + "\")", "base");
        }
        return base;
    }

    private List<String> normalizeQuotes(String quotesParam) {
        if (quotesParam == null || quotesParam.isBlank()) {
            return DEFAULT_QUOTES;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String segment : quotesParam.split(",")) {
            String code = segment.trim().toUpperCase(Locale.ROOT);
            if (!code.isEmpty()) {
                normalized.add(code);
            }
        }
        if (normalized.isEmpty()) {
            return DEFAULT_QUOTES;
        }
        if (normalized.stream().anyMatch(code -> !ISO_4217.matcher(code).matches())) {
            throw new InvalidRequestException(
                    "quotes must be a 3-letter ISO-4217 code or a comma-separated list of them (got \""
                            + quotesParam + "\")",
                    "quotes");
        }
        return List.copyOf(normalized);
    }

    private String requireCode(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException(name + " is required", name);
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!ISO_4217.matcher(code).matches()) {
            throw new InvalidRequestException(
                    name + " must be a 3-letter ISO-4217 code (got \"" + raw + "\")", name);
        }
        return code;
    }

    private LocalDate requireDate(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException(name + " is required", name);
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(
                    name + " must be an ISO date (YYYY-MM-DD) (got \"" + raw + "\")", name);
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException(
                    "from must not be after to (got " + from + " > " + to + ")", "from");
        }
        LocalDate today = LocalDate.now();
        if (from.isAfter(today) || to.isAfter(today)) {
            throw new InvalidRequestException(
                    "from and to must not be in the future (got " + from + " to " + to + ")",
                    "from", "to");
        }
        long span = ChronoUnit.DAYS.between(from, to) + 1;
        if (span > MAX_SPAN_DAYS) {
            throw new InvalidRequestException(
                    "range must not exceed " + MAX_SPAN_DAYS + " days (got " + span + ")", "from",
                    "to");
        }
    }

    private static List<String> sortedCopy(List<String> quotes) {
        List<String> sorted = new ArrayList<>(quotes);
        sorted.sort(String::compareTo);
        return sorted;
    }
}
