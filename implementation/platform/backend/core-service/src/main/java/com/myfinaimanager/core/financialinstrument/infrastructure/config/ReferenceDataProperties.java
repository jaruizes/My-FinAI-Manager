package com.myfinaimanager.core.financialinstrument.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the reference-data ingestion (EN004; DR-032). Bound from the
 * {@code app.reference-data.*} block in {@code application.yml}. All values are {@code classpath:}
 * resource locations of committed CSV files — no secret, no host path, no runtime mount.
 *
 * @param importOnStartup      run the import once on application start (idempotent; {@code false} in test slices)
 * @param marketsFile          ISO 10383 – compatible Market CSV
 * @param instrumentsFile      Yahoo-shape instrument CSV (curated deterministic subset)
 * @param exchangeMicMappingFile  Yahoo {@code Exchange} → canonical MIC / currency / supported_for_fd002
 * @param suffixOverrideFile   {@code (Exchange, suffix)} → canonical MIC / currency overrides
 */
@ConfigurationProperties("app.reference-data")
public record ReferenceDataProperties(
        boolean importOnStartup,
        String marketsFile,
        String instrumentsFile,
        String exchangeMicMappingFile,
        String suffixOverrideFile) {
}
