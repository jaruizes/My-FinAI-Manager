/**
 * Module wiring for {@code marketdata} (ADR-003 — configuration is infrastructure):
 * {@code FinnhubProperties} ({@code @ConfigurationProperties("finnhub")}) and
 * {@code MarketDataModuleConfiguration} (the {@code FinnhubRestClient} bean with explicit timeouts,
 * a fallback {@code Clock}, and the "integration disabled" startup log when no API key is set).
 * The API key is never stored, logged, or exposed here.
 */
package com.myfinaimanager.core.marketdata.infrastructure.config;
