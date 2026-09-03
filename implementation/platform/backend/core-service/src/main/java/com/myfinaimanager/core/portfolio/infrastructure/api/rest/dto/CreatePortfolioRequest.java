package com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto;

import java.util.List;

/**
 * Request body for {@code POST /api/portfolios}. Shape mirrors the {@code CreatePortfolioRequest}
 * schema in {@code implementation/platform/contracts/openapi/openapi.yaml}. All values are strings
 * and are passed to the domain verbatim — the domain does the parsing and validation
 * (research.md D2). This is NOT a domain type.
 */
public record CreatePortfolioRequest(String name, List<PositionInput> positions) {

    public record PositionInput(
            String ticker,
            String market,
            String quantity,
            String currency,
            String initialPurchaseDate,
            String averagePurchasePrice) {
    }
}
