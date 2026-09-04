package com.myfinaimanager.core.portfolio.domain.ports;

import com.myfinaimanager.core.portfolio.domain.model.FxConversion;
import com.myfinaimanager.core.portfolio.domain.model.PositionPricing;
import java.util.Optional;

/**
 * Anti-corruption port over the {@code marketdata} module (EN005) for FD004 valuation.
 *
 * <p>AR-062: an inter-module read goes through <em>this</em> module's published port, and only from
 * {@code portfolio.infrastructure}. The adapter ({@code EnMarketDataGatewayAdapter} in
 * {@code portfolio.infrastructure.marketdata}) is the single class allowed to import
 * {@code com.myfinaimanager.core.marketdata.*}. It translates every {@code MarketDataException}
 * (and any malformed argument) into {@link Optional#empty()} — a single "this input is unavailable"
 * signal the deterministic valuation understands (FR-017, FR-018, FR-026, FR-035).
 *
 * <p>Arguments are primitive {@code String}s so no {@code marketdata} enum or value object reaches
 * {@code portfolio.domain}. Currency codes are ISO-4217 3-letter strings ({@code "EUR"}, {@code "USD"});
 * an unrecognised value yields {@link Optional#empty()}, never an exception.
 */
public interface MarketDataGateway {

    /** Latest market price for the instrument in its native currency; empty when unavailable for any reason. */
    Optional<PositionPricing> latestPrice(String ticker, String market, String currencyCode);

    /**
     * Provider sector/industry classification string for the instrument; empty when the profile is
     * unavailable <em>or</em> the provider returned an unclassified profile.
     */
    Optional<String> sector(String ticker, String market, String currencyCode);

    /** FX rate {@code fromCurrencyCode -> toCurrencyCode}; empty when unavailable for any reason. */
    Optional<FxConversion> fxRate(String fromCurrencyCode, String toCurrencyCode);
}
