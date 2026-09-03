package com.myfinaimanager.core.financialinstrument.infrastructure.api.rest.dto;

/**
 * Response body for one catalog listing on {@code GET /api/financial-instruments} — the shape of
 * the {@code FinancialInstrument} schema in the OpenAPI contract. Business fields only:
 * {@code providerSymbol}, provider {@code Exchange}, {@code operatingMic}, {@code instrumentType},
 * {@code externalReference}, and all {@code source*} provenance are deliberately absent
 * (spec FR-028; VC-010). {@code isin} is {@code null} when the catalog has none.
 */
public record FinancialInstrumentResponse(
        String id,
        String name,
        String ticker,
        String market,
        String currency,
        Boolean active,
        String isin) {
}
