package com.myfinaimanager.core.financialinstrument.domain.model;

/**
 * Raw, unvalidated Market input (all strings) — the carrier between an infrastructure source
 * adapter and {@link Market#fromRaw(NewMarket)}, which parses and validates it. Mirrors the FD001
 * {@code NewPosition} pattern. {@code countryIso2}, {@code operatingMic}, {@code active} may be
 * {@code null} / blank.
 */
public record NewMarket(
        String mic,
        String name,
        String countryIso2,
        String operatingMic,
        String active) {
}
