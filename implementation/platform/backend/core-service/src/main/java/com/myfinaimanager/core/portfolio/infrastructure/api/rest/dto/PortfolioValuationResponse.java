package com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/portfolios/{portfolioId}/valuation} — mirrors the
 * {@code PortfolioValuation} schema in the OpenAPI contract. Decimal values are plain strings,
 * full precision (the 2-decimal display rounding is the client's job — FR-031). A missing value is
 * {@code null}, never {@code "0"} (FR-019). Built by {@code PortfolioValuationResponseMapper}.
 */
public record PortfolioValuationResponse(
        String portfolioId,
        String status,
        String calculatedAt,
        String totalValueEUR,
        String totalValueUSD,
        String marketDataAsOf,
        String fxDataAsOf,
        List<PositionValuationResponse> positions,
        List<SectorAllocationResponse> sectors) {

    public record PositionValuationResponse(
            String ticker,
            String market,
            String quantity,
            String nativeCurrency,
            boolean valued,
            String marketPrice,
            String nativeMarketValue,
            String valueInEUR,
            String valueInUSD,
            String portfolioWeight,
            String sector,
            String priceObservedAt) {
    }

    public record SectorAllocationResponse(
            String sector,
            String sectorValueEUR,
            String sectorWeight) {
    }
}
