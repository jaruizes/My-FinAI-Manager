package com.myfinaimanager.core.marketdata.infrastructure.frankfurter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Frankfurter {@code GET /v1/latest?base={from}&symbols={to}} response. {@code rates} maps a
 * currency code to its rate relative to {@code base}; {@code date} is the ECB publication date (no
 * time). EN005 reads only the single {@code from -> to} entry it asked for. Infrastructure-only —
 * confined to the {@code frankfurter} adapter by ArchUnit.
 *
 * <p>See {@code specs/EN005-…/contracts/frankfurter-provider-contract.md}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FrankfurterRatesResponse(
        BigDecimal amount,
        String base,
        String date,
        Map<String, BigDecimal> rates) {
}
