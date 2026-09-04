package com.myfinaimanager.core.portfolio.infrastructure.marketdata;

import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataException;
import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.domain.ports.FxRatePort;
import com.myfinaimanager.core.marketdata.domain.ports.InstrumentProfilePort;
import com.myfinaimanager.core.marketdata.domain.ports.MarketDataPort;
import com.myfinaimanager.core.portfolio.domain.model.FxConversion;
import com.myfinaimanager.core.portfolio.domain.model.PositionPricing;
import com.myfinaimanager.core.portfolio.domain.ports.MarketDataGateway;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The single adapter through which the {@code portfolio} module reads the {@code marketdata} module
 * (EN005) for FD004 valuation (AR-062). It is the <strong>only</strong> {@code portfolio} class
 * that imports {@code com.myfinaimanager.core.marketdata.*}
 * ({@code StandardArchitectureRulesTest} enforces this).
 *
 * <p>Every {@code marketdata} failure — not resolved, provider error/timeout, rate-limited, not
 * configured — is translated to {@link Optional#empty()}. The deterministic valuation then treats
 * that Position/rate as "unavailable" (FR-017, FR-018) rather than seeing a provider exception or
 * a fabricated value (FR-026, FR-035).
 */
@Component
public class EnMarketDataGatewayAdapter implements MarketDataGateway {

    private static final Logger log = LoggerFactory.getLogger(EnMarketDataGatewayAdapter.class);

    private final MarketDataPort marketDataPort;
    private final InstrumentProfilePort instrumentProfilePort;
    private final FxRatePort fxRatePort;

    public EnMarketDataGatewayAdapter(MarketDataPort marketDataPort,
                                      InstrumentProfilePort instrumentProfilePort,
                                      FxRatePort fxRatePort) {
        this.marketDataPort = marketDataPort;
        this.instrumentProfilePort = instrumentProfilePort;
        this.fxRatePort = fxRatePort;
    }

    @Override
    public Optional<PositionPricing> latestPrice(String ticker, String market, String currencyCode) {
        InstrumentIdentifier instrument = identifier(ticker, market, currencyCode);
        if (instrument == null) {
            return Optional.empty();
        }
        try {
            MarketPrice price = marketDataPort.getLatestPrice(instrument);
            return Optional.of(new PositionPricing(price.price(), price.observedAt()));
        } catch (MarketDataException e) {
            logUnavailable("price", ticker, market, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> sector(String ticker, String market, String currencyCode) {
        InstrumentIdentifier instrument = identifier(ticker, market, currencyCode);
        if (instrument == null) {
            return Optional.empty();
        }
        try {
            InstrumentProfile profile = instrumentProfilePort.getProfile(instrument);
            return profile.sector().isClassified()
                    ? Optional.of(profile.sector().classification())
                    : Optional.empty();
        } catch (MarketDataException e) {
            logUnavailable("profile", ticker, market, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<FxConversion> fxRate(String fromCurrencyCode, String toCurrencyCode) {
        SupportedCurrency from = SupportedCurrency.parseOrNull(fromCurrencyCode);
        SupportedCurrency to = SupportedCurrency.parseOrNull(toCurrencyCode);
        if (from == null || to == null || from == to) {
            return Optional.empty();
        }
        try {
            FxRate rate = fxRatePort.getRate(from, to);
            return Optional.of(new FxConversion(rate.rate(), rate.observedAt()));
        } catch (MarketDataException | IllegalArgumentException e) {
            logUnavailable("fx", fromCurrencyCode, toCurrencyCode, e);
            return Optional.empty();
        }
    }

    private static InstrumentIdentifier identifier(String ticker, String market, String currencyCode) {
        SupportedCurrency currency = SupportedCurrency.parseOrNull(currencyCode);
        if (currency == null) {
            return null;
        }
        try {
            return new InstrumentIdentifier(ticker, market, currency);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void logUnavailable(String kind, String left, String right, RuntimeException cause) {
        log.atDebug()
                .addKeyValue("event", "MarketDataUnavailable")
                .addKeyValue("kind", kind)
                .addKeyValue("left", left)
                .addKeyValue("right", right)
                .addKeyValue("reason", cause.getClass().getSimpleName())
                .log("market data unavailable for valuation");
    }
}
