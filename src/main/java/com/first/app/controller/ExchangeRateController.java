package com.first.app.controller;

import com.first.app.dto.ExchangeRateHistoryResponse;
import com.first.app.dto.ExchangeRateLatestResponse;
import com.first.app.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the shared rate service — thin delegation only: defaults, code/date
 * normalization and validation, provider routing, and caching live in
 * {@link ExchangeRateService}.
 */
@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @Operation(summary = "Latest published rates for a base currency (defaults: CNY, USD/EUR/JPY/KRW)")
    @GetMapping("/latest")
    public ResponseEntity<ExchangeRateLatestResponse> latest(
            @RequestParam(required = false) String base,
            @RequestParam(required = false) String quotes) {
        return ResponseEntity.ok(exchangeRateService.getLatest(base, quotes));
    }

    @Operation(summary = "Daily reference series for one currency pair (max 366 days)")
    @GetMapping("/history")
    public ResponseEntity<ExchangeRateHistoryResponse> history(
            @RequestParam(required = false) String base,
            @RequestParam(required = false) String quote,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(exchangeRateService.getHistory(base, quote, from, to));
    }
}
