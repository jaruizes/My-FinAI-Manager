package com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Finnhub {@code GET /stock/profile2} response (subset). {@code exchange} is provider metadata only
 * and MUST NOT become a canonical MIC (VC-010). An empty object ({@code {}}) is Finnhub's answer for
 * an unknown symbol. Infrastructure-only — confined to this package by ArchUnit.
 *
 * <p>EN005 Revision 2: the instrument-profile capability moves to the {@code financialinstrument}
 * module; this DTO is retained here only until that move (Checkpoint C3), then deleted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubCompanyProfileResponse(
        String ticker,
        String name,
        String currency,
        String exchange,
        String finnhubIndustry) {

    public boolean isEmpty() {
        return (name == null || name.isBlank())
                && (ticker == null || ticker.isBlank())
                && (finnhubIndustry == null || finnhubIndustry.isBlank());
    }
}
