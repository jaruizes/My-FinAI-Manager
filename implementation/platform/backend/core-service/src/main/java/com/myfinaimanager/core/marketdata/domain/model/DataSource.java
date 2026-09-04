package com.myfinaimanager.core.marketdata.domain.model;

/**
 * The external source a market-data result came from. Kept as a type (not a free string) so a future
 * feature can react to provenance, and so adding a second provider (e.g. an ECB FX adapter) is an
 * additive change.
 */
public enum DataSource {
    FINNHUB,
    FRANKFURTER
}
