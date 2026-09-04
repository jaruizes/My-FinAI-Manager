/**
 * The {@code portfolio} module's anti-corruption adapter over the {@code marketdata} module (EN005),
 * for FD004 valuation. {@code EnMarketDataGatewayAdapter} implements
 * {@code portfolio.domain.ports.MarketDataGateway} and is the <strong>only</strong> class in the
 * {@code portfolio} module allowed to import {@code com.myfinaimanager.core.marketdata.*}
 * (AR-062; enforced by {@code StandardArchitectureRulesTest}). It translates every
 * {@code MarketDataException} into {@link java.util.Optional#empty()} so the deterministic
 * valuation only ever sees "available" or "unavailable" (FR-026, FR-035).
 */
package com.myfinaimanager.core.portfolio.infrastructure.marketdata;
