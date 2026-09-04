package com.myfinaimanager.core.portfolio.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.FxConversion;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.NewPosition;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PositionPricing;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.portfolio.domain.ports.MarketDataGateway;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioValuationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioValuationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant TS = Instant.parse("2026-09-04T11:00:00Z");
    private static final InvestorId INVESTOR =
            InvestorId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Mock private PortfolioRepository portfolios;
    @Mock private PortfolioValuationRepository valuations;
    @Mock private MarketDataGateway marketData;
    @Mock private DefaultInvestorProvider defaultInvestorProvider;

    private PortfolioValuationService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioValuationService(portfolios, valuations, marketData,
                defaultInvestorProvider, CLOCK);
    }

    private static Portfolio twoPositionPortfolio() {
        return Portfolio.create(INVESTOR, "Growth", List.of(
                new NewPosition("AAPL", "XNAS", "10", "USD", null, null),
                new NewPosition("SAN", "XMAD", "100", "EUR", null, null)), CLOCK);
    }

    private void gatewayReturnsE2E001Values() {
        when(marketData.latestPrice("AAPL", "XNAS", "USD"))
                .thenReturn(Optional.of(new PositionPricing(new BigDecimal("200"), TS)));
        when(marketData.latestPrice("SAN", "XMAD", "EUR"))
                .thenReturn(Optional.of(new PositionPricing(new BigDecimal("5"), TS)));
        when(marketData.sector("AAPL", "XNAS", "USD")).thenReturn(Optional.of("Technology"));
        when(marketData.sector("SAN", "XMAD", "EUR")).thenReturn(Optional.of("Financial Services"));
        when(marketData.fxRate("USD", "EUR"))
                .thenReturn(Optional.of(new FxConversion(new BigDecimal("0.80"), TS)));
        when(marketData.fxRate("EUR", "USD"))
                .thenReturn(Optional.of(new FxConversion(new BigDecimal("1.25"), TS)));
    }

    @Test
    void values_the_portfolio_and_persists_one_snapshot_without_ever_writing_the_portfolio() {
        Portfolio portfolio = twoPositionPortfolio();
        when(defaultInvestorProvider.get()).thenReturn(INVESTOR);
        when(portfolios.findByIdForInvestor(eq(portfolio.id()), eq(INVESTOR)))
                .thenReturn(Optional.of(portfolio));
        gatewayReturnsE2E001Values();

        service.value(portfolio.id());

        ArgumentCaptor<PortfolioValuation> saved = ArgumentCaptor.forClass(PortfolioValuation.class);
        verify(valuations).upsertLatest(saved.capture());
        verify(portfolios, never()).save(any(), any());

        PortfolioValuation v = saved.getValue();
        assertThat(v.status()).isEqualTo(ValuationStatus.COMPLETED);
        assertThat(v.totalValueEUR().orElseThrow()).isEqualByComparingTo("2100.00");
        assertThat(v.totalValueUSD().orElseThrow()).isEqualByComparingTo("2625.00");
    }

    @Test
    void a_position_with_no_price_makes_the_valuation_partial() {
        Portfolio portfolio = twoPositionPortfolio();
        when(defaultInvestorProvider.get()).thenReturn(INVESTOR);
        when(portfolios.findByIdForInvestor(any(), any())).thenReturn(Optional.of(portfolio));
        when(marketData.latestPrice("AAPL", "XNAS", "USD"))
                .thenReturn(Optional.of(new PositionPricing(new BigDecimal("200"), TS)));
        when(marketData.latestPrice("SAN", "XMAD", "EUR")).thenReturn(Optional.empty());
        when(marketData.sector(any(), any(), any())).thenReturn(Optional.empty());
        when(marketData.fxRate("USD", "EUR"))
                .thenReturn(Optional.of(new FxConversion(new BigDecimal("0.80"), TS)));
        when(marketData.fxRate("EUR", "USD"))
                .thenReturn(Optional.of(new FxConversion(new BigDecimal("1.25"), TS)));

        service.value(portfolio.id());

        ArgumentCaptor<PortfolioValuation> saved = ArgumentCaptor.forClass(PortfolioValuation.class);
        verify(valuations).upsertLatest(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ValuationStatus.PARTIAL);
        assertThat(saved.getValue().positions())
                .filteredOn(p -> p.ticker().equals("SAN"))
                .singleElement()
                .satisfies(p -> assertThat(p.valued()).isFalse());
    }

    @Test
    void a_full_market_data_outage_produces_a_failed_snapshot() {
        Portfolio portfolio = twoPositionPortfolio();
        when(defaultInvestorProvider.get()).thenReturn(INVESTOR);
        when(portfolios.findByIdForInvestor(any(), any())).thenReturn(Optional.of(portfolio));
        when(marketData.latestPrice(any(), any(), any())).thenReturn(Optional.empty());
        when(marketData.sector(any(), any(), any())).thenReturn(Optional.empty());
        when(marketData.fxRate(any(), any())).thenReturn(Optional.empty());

        service.value(portfolio.id());

        ArgumentCaptor<PortfolioValuation> saved = ArgumentCaptor.forClass(PortfolioValuation.class);
        verify(valuations).upsertLatest(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ValuationStatus.FAILED);
    }

    @Test
    void an_unknown_portfolio_id_is_a_not_found() {
        PortfolioId unknown = PortfolioId.newId();
        when(defaultInvestorProvider.get()).thenReturn(INVESTOR);
        when(portfolios.findByIdForInvestor(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.value(unknown)).isInstanceOf(PortfolioNotFoundException.class);
        verify(valuations, never()).upsertLatest(any());
    }

    @Test
    void find_latest_scopes_to_the_current_investor_and_returns_the_snapshot() {
        PortfolioId id = PortfolioId.newId();
        Portfolio portfolio = twoPositionPortfolio();
        when(defaultInvestorProvider.get()).thenReturn(INVESTOR);
        when(portfolios.findByIdForInvestor(eq(id), eq(INVESTOR))).thenReturn(Optional.of(portfolio));
        PortfolioValuation snapshot = new PortfolioValuation(id, ValuationStatus.PARTIAL, TS,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), List.of());
        when(valuations.findByPortfolioId(id)).thenReturn(Optional.of(snapshot));

        assertThat(service.findLatest(id)).contains(snapshot);
    }

    @Test
    void find_latest_throws_not_found_for_another_investors_portfolio() {
        PortfolioId id = PortfolioId.newId();
        when(defaultInvestorProvider.get()).thenReturn(INVESTOR);
        when(portfolios.findByIdForInvestor(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findLatest(id)).isInstanceOf(PortfolioNotFoundException.class);
    }
}
