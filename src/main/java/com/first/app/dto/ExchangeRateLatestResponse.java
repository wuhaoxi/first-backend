package com.first.app.dto;

import com.first.app.integration.ExchangeRateApiClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Latest published rates for one base. Quotes follow request order — never provider order —
 * and {@code asOfDate} is the provider's publication date (daily granularity, not live).
 */
public record ExchangeRateLatestResponse(
        String base,
        String asOfDate,
        List<Quote> quotes,
        String attribution) {

    public static final String ATTRIBUTION = "Rates By Exchange Rate API";

    public static ExchangeRateLatestResponse from(ExchangeRateApiClient.LatestRates latest) {
        return new ExchangeRateLatestResponse(latest.base(), latest.asOfDate(),
                latest.quotes().stream().map(Quote::from).toList(), ATTRIBUTION);
    }

    /**
     * Returns a copy whose quotes follow the caller's request order. The payload carries exactly
     * the requested currencies (the cache key is their sorted set), so a reordered request served
     * from an entry populated in a different order still answers in request order.
     */
    public ExchangeRateLatestResponse withQuotesInOrder(List<String> requestOrder) {
        Map<String, Double> ratesByCurrency = quotes.stream()
                .collect(Collectors.toMap(Quote::currency, Quote::rate));
        return new ExchangeRateLatestResponse(base, asOfDate,
                requestOrder.stream()
                        .map(currency -> new Quote(currency, ratesByCurrency.get(currency)))
                        .toList(),
                attribution);
    }

    public record Quote(String currency, double rate) {

        static Quote from(ExchangeRateApiClient.Quote quote) {
            return new Quote(quote.currency(), quote.rate());
        }
    }
}
