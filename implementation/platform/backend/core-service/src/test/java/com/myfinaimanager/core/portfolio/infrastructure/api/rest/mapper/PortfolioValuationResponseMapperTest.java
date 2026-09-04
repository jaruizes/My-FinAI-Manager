package com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PositionValuation;
import com.myfinaimanager.core.portfolio.domain.model.SectorAllocation;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.PortfolioValuationResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioValuationResponseMapperTest {

    private static final PortfolioId ID = PortfolioId.newId();
    private static final Instant AT = Instant.parse("2026-09-04T12:00:00Z");

    private final PortfolioValuationResponseMapper mapper = new PortfolioValuationResponseMapper();

    @Test
    void maps_a_completed_snapshot_with_full_precision_strings() {
        PositionValuation aapl = new PositionValuation("AAPL", "XNAS", new BigDecimal("10"), "USD",
                true, Optional.of(new BigDecimal("200")), Optional.of(new BigDecimal("2000")),
                Optional.of(new BigDecimal("1600.00")), Optional.of(new BigDecimal("2000")),
                Optional.of(new BigDecimal("0.761904761905")), "Technology", Optional.of(AT));
        PortfolioValuation v = new PortfolioValuation(ID, ValuationStatus.COMPLETED, AT,
                Optional.of(new BigDecimal("2100.00")), Optional.of(new BigDecimal("2625.00")),
                Optional.of(AT), Optional.of(AT), List.of(aapl),
                List.of(new SectorAllocation("Technology", new BigDecimal("1600"),
                        new BigDecimal("0.761904761905"))));

        PortfolioValuationResponse dto = mapper.toResponse(v);

        assertThat(dto.status()).isEqualTo("COMPLETED");
        assertThat(dto.totalValueEUR()).isEqualTo("2100.00");
        assertThat(dto.totalValueUSD()).isEqualTo("2625.00");
        assertThat(dto.positions()).singleElement().satisfies(p -> {
            assertThat(p.valued()).isTrue();
            assertThat(p.marketPrice()).isEqualTo("200");
            assertThat(p.valueInEUR()).isEqualTo("1600.00");
            assertThat(p.portfolioWeight()).isEqualTo("0.761904761905");
            assertThat(p.sector()).isEqualTo("Technology");
        });
        assertThat(dto.sectors()).singleElement().satisfies(s -> {
            assertThat(s.sector()).isEqualTo("Technology");
            assertThat(s.sectorWeight()).isEqualTo("0.761904761905");
        });
    }

    @Test
    void an_unvalued_position_has_null_money_fields_never_zero() { // FR-019
        PositionValuation unvalued = PositionValuation.unvalued("SAN", "XMAD",
                new BigDecimal("100"), "EUR", "Unclassified");
        PortfolioValuation v = new PortfolioValuation(ID, ValuationStatus.PARTIAL, AT,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(unvalued), List.of());

        PortfolioValuationResponse dto = mapper.toResponse(v);

        assertThat(dto.totalValueEUR()).isNull();
        assertThat(dto.totalValueUSD()).isNull();
        assertThat(dto.positions()).singleElement().satisfies(p -> {
            assertThat(p.valued()).isFalse();
            assertThat(p.marketPrice()).isNull();
            assertThat(p.nativeMarketValue()).isNull();
            assertThat(p.valueInEUR()).isNull();
            assertThat(p.valueInUSD()).isNull();
            assertThat(p.portfolioWeight()).isNull();
        });
    }

    @Test
    void failed_snapshot_maps_with_no_totals() {
        PortfolioValuation v = new PortfolioValuation(ID, ValuationStatus.FAILED, AT,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), List.of());

        PortfolioValuationResponse dto = mapper.toResponse(v);

        assertThat(dto.status()).isEqualTo("FAILED");
        assertThat(dto.calculatedAt()).isEqualTo(AT.toString());
        assertThat(dto.positions()).isEmpty();
        assertThat(dto.sectors()).isEmpty();
    }

    @Test
    void pending_placeholder_has_status_pending_and_null_everything_else() { // FR-025
        PortfolioValuationResponse dto = mapper.pending(ID);

        assertThat(dto.portfolioId()).isEqualTo(ID.toString());
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.calculatedAt()).isNull();
        assertThat(dto.totalValueEUR()).isNull();
        assertThat(dto.positions()).isEmpty();
        assertThat(dto.sectors()).isEmpty();
    }
}
