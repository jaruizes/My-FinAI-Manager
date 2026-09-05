package com.myfinaimanager.core.portfolioanalysis.infrastructure.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.portfolio.business.PortfolioQueryUseCase;
import com.myfinaimanager.core.portfolio.business.PortfolioValuationQueryUseCase;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioName;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioStatus;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PositionValuation;
import com.myfinaimanager.core.portfolio.domain.model.SectorAllocation;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioValuationStatus;

/**
 * Maps FD004's {@code Portfolio}/{@code PortfolioValuation}/{@code PositionValuation}/
 * {@code SectorAllocation} into {@link PortfolioContextSnapshot} for COMPLETED/PARTIAL/FAILED/
 * absent valuation — the sole importer of {@code portfolio.*} (contract C1, P1, P2).
 */
class PortfolioContextGatewayAdapterTest {

    private static final UUID PORTFOLIO_UUID = UUID.randomUUID();
    private static final PortfolioId PORTFOLIO_ID = PortfolioId.of(PORTFOLIO_UUID);
    private static final Instant CALCULATED_AT = Instant.parse("2026-09-05T10:00:00Z");

    private static Portfolio portfolio() {
        return Portfolio.reconstitute(
                PORTFOLIO_ID, InvestorId.of(UUID.randomUUID()), new PortfolioName("Long Term Investment"),
                PortfolioStatus.ACTIVE, List.of(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static PortfolioQueryUseCase portfolioQueryUseCase() {
        PortfolioQueryUseCase useCase = mock(PortfolioQueryUseCase.class);
        when(useCase.view(PORTFOLIO_ID)).thenReturn(portfolio());
        return useCase;
    }

    @Test
    void a_completed_valuation_maps_status_totals_positions_and_sectors() {
        PositionValuation aapl = new PositionValuation(
                "AAPL", "NASDAQ", BigDecimal.TEN, "USD", true, Optional.of(BigDecimal.TEN),
                Optional.of(new BigDecimal("100")), Optional.of(new BigDecimal("95")),
                Optional.of(new BigDecimal("100")), Optional.of(new BigDecimal("0.76")), "Technology",
                Optional.of(CALCULATED_AT));
        SectorAllocation tech = new SectorAllocation("Technology", new BigDecimal("95"), new BigDecimal("0.76"));
        PortfolioValuation valuation = new PortfolioValuation(
                PORTFOLIO_ID, ValuationStatus.COMPLETED, CALCULATED_AT, Optional.of(new BigDecimal("95")),
                Optional.of(new BigDecimal("100")), Optional.of(CALCULATED_AT), Optional.of(CALCULATED_AT),
                List.of(aapl), List.of(tech));
        PortfolioValuationQueryUseCase valuationUseCase = mock(PortfolioValuationQueryUseCase.class);
        when(valuationUseCase.findLatest(PORTFOLIO_ID)).thenReturn(Optional.of(valuation));
        PortfolioContextGatewayAdapter adapter =
                new PortfolioContextGatewayAdapter(portfolioQueryUseCase(), valuationUseCase);

        PortfolioContextSnapshot snapshot = adapter.fetch(PORTFOLIO_UUID);

        assertThat(snapshot.portfolioName()).isEqualTo("Long Term Investment");
        assertThat(snapshot.valuationStatus()).isEqualTo(PortfolioValuationStatus.COMPLETED);
        assertThat(snapshot.totalValueEur()).contains(new BigDecimal("95"));
        assertThat(snapshot.totalValueUsd()).contains(new BigDecimal("100"));
        assertThat(snapshot.valuedAt()).contains(CALCULATED_AT);
        assertThat(snapshot.positions()).hasSize(1);
        assertThat(snapshot.positions().get(0).ticker()).isEqualTo("AAPL");
        assertThat(snapshot.positions().get(0).weight()).contains(new BigDecimal("0.76"));
        assertThat(snapshot.positions().get(0).valueEur()).contains(new BigDecimal("95"));
        assertThat(snapshot.positions().get(0).sector()).contains("Technology");
        assertThat(snapshot.positions().get(0).currency()).isEqualTo("USD");
        assertThat(snapshot.sectors()).hasSize(1);
        assertThat(snapshot.sectors().get(0).sector()).isEqualTo("Technology");
    }

    @Test
    void a_partial_valuation_maps_unvalued_positions_with_empty_monetary_fields() {
        PositionValuation unvalued =
                PositionValuation.unvalued("XYZ", "LSE", BigDecimal.ONE, "GBP", "Unclassified");
        PortfolioValuation valuation = new PortfolioValuation(
                PORTFOLIO_ID, ValuationStatus.PARTIAL, CALCULATED_AT, Optional.of(BigDecimal.TEN),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(unvalued), List.of());
        PortfolioValuationQueryUseCase valuationUseCase = mock(PortfolioValuationQueryUseCase.class);
        when(valuationUseCase.findLatest(PORTFOLIO_ID)).thenReturn(Optional.of(valuation));
        PortfolioContextGatewayAdapter adapter =
                new PortfolioContextGatewayAdapter(portfolioQueryUseCase(), valuationUseCase);

        PortfolioContextSnapshot snapshot = adapter.fetch(PORTFOLIO_UUID);

        assertThat(snapshot.valuationStatus()).isEqualTo(PortfolioValuationStatus.PARTIAL);
        assertThat(snapshot.positions().get(0).weight()).isEmpty();
        assertThat(snapshot.positions().get(0).valueEur()).isEmpty();
    }

    @Test
    void a_failed_valuation_maps_to_the_failed_status_with_no_totals() {
        PortfolioValuation valuation = new PortfolioValuation(
                PORTFOLIO_ID, ValuationStatus.FAILED, CALCULATED_AT, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of(), List.of());
        PortfolioValuationQueryUseCase valuationUseCase = mock(PortfolioValuationQueryUseCase.class);
        when(valuationUseCase.findLatest(PORTFOLIO_ID)).thenReturn(Optional.of(valuation));
        PortfolioContextGatewayAdapter adapter =
                new PortfolioContextGatewayAdapter(portfolioQueryUseCase(), valuationUseCase);

        PortfolioContextSnapshot snapshot = adapter.fetch(PORTFOLIO_UUID);

        assertThat(snapshot.valuationStatus()).isEqualTo(PortfolioValuationStatus.FAILED);
        assertThat(snapshot.totalValueEur()).isEmpty();
    }

    @Test
    void an_absent_valuation_maps_to_the_local_absent_status_with_no_positions() {
        PortfolioValuationQueryUseCase valuationUseCase = mock(PortfolioValuationQueryUseCase.class);
        when(valuationUseCase.findLatest(PORTFOLIO_ID)).thenReturn(Optional.empty());
        PortfolioContextGatewayAdapter adapter =
                new PortfolioContextGatewayAdapter(portfolioQueryUseCase(), valuationUseCase);

        PortfolioContextSnapshot snapshot = adapter.fetch(PORTFOLIO_UUID);

        assertThat(snapshot.valuationStatus()).isEqualTo(PortfolioValuationStatus.ABSENT);
        assertThat(snapshot.positions()).isEmpty();
        assertThat(snapshot.sectors()).isEmpty();
        assertThat(snapshot.valuedAt()).isEmpty();
    }

    @Test
    void an_unknown_portfolio_is_translated_into_this_modules_own_not_found_exception() {
        PortfolioQueryUseCase portfolioQueryUseCase = mock(PortfolioQueryUseCase.class);
        when(portfolioQueryUseCase.view(PORTFOLIO_ID))
                .thenThrow(new com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException(
                        PORTFOLIO_ID));
        PortfolioContextGatewayAdapter adapter =
                new PortfolioContextGatewayAdapter(portfolioQueryUseCase, mock(PortfolioValuationQueryUseCase.class));

        assertThatThrownBy(() -> adapter.fetch(PORTFOLIO_UUID))
                .isInstanceOf(com.myfinaimanager.core.portfolioanalysis.domain.exceptions.PortfolioNotFoundException.class)
                .isNotInstanceOf(com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException.class);
    }
}
