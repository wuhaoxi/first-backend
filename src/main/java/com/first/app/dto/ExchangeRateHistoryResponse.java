package com.first.app.dto;

import com.first.app.integration.FrankfurterClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily series for exactly one currency pair. Frankfurter publishes business days only, so
 * {@code requestedDays} (the inclusive span) and {@code returnedDays} (published days) differ
 * for ranges covering weekends and holidays; gaps are preserved, never interpolated.
 */
public record ExchangeRateHistoryResponse(
        String base,
        String quote,
        String fromDate,
        String toDate,
        List<SeriesPoint> series,
        int requestedDays,
        int returnedDays,
        String attribution) {

    public static final String ATTRIBUTION = "Daily reference rates via Frankfurter";

    public static ExchangeRateHistoryResponse from(String base, String quote, String fromDate,
            String toDate, List<FrankfurterClient.SeriesPoint> series) {
        int requestedDays = (int) (ChronoUnit.DAYS.between(LocalDate.parse(fromDate),
                LocalDate.parse(toDate)) + 1);
        return new ExchangeRateHistoryResponse(base, quote, fromDate, toDate,
                series.stream().map(SeriesPoint::from).toList(), requestedDays, series.size(),
                ATTRIBUTION);
    }

    public record SeriesPoint(String date, double rate) {

        static SeriesPoint from(FrankfurterClient.SeriesPoint point) {
            return new SeriesPoint(point.date(), point.rate());
        }
    }
}
