/**
 * Market Data integration module (EN005). ADR-003 module-first: {@code domain} / {@code infrastructure}
 * with dependencies pointing inward. Provides three provider-neutral outbound ports —
 * {@code MarketDataPort}, {@code InstrumentProfilePort}, {@code FxRatePort} — with Finnhub as the
 * initial provider, isolated entirely behind {@code infrastructure.finnhub} adapters.
 *
 * <p>There is intentionally <strong>no {@code business} package</strong>: the ports are outbound and
 * are consumed by a future Portfolio-valuation / sector-allocation feature, not by this module
 * (plan.md OD-EN005-10). EN005 adds no persistence, no Flyway migration, no external REST API, and
 * no frontend code.
 */
package com.myfinaimanager.core.marketdata;
