package com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto;

import java.util.List;

/**
 * Response body for a created / replayed portfolio. Shape mirrors the {@code Portfolio} schema in
 * the OpenAPI contract. Decimal values are plain strings, exactly as stored (no precision loss).
 * Built by {@code PortfolioResponseMapper} — this record holds no domain reference.
 */
public record CreatePortfolioResponse(
        String id,
        String name,
        String status,
        List<PositionResponse> positions,
        String createdAt) {

    public record PositionResponse(
            String id,
            String ticker,
            String market,
            String quantity,
            String currency,
            String initialPurchaseDate,
            String averagePurchasePrice) {
    }
}
