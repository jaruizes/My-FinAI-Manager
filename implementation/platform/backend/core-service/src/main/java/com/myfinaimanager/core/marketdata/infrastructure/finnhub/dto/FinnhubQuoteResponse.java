package com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Finnhub {@code GET /quote} response. Only {@code c} (current price) and {@code t} (unix seconds)
 * are consumed by EN005; the other fields are bound for completeness and are not exposed by any
 * port (enabler §8). Infrastructure-only — confined to this package by ArchUnit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubQuoteResponse(
        BigDecimal c,
        BigDecimal d,
        BigDecimal dp,
        BigDecimal h,
        BigDecimal l,
        BigDecimal o,
        BigDecimal pc,
        Long t) {
}
