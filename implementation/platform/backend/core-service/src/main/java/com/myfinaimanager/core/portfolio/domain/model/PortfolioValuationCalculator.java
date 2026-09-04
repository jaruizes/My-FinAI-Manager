package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The deterministic heart of FD004: a <strong>pure</strong> function from a Portfolio's Position
 * inputs + FX context to a {@link PortfolioValuation} snapshot. No ports, no Spring, no I/O, no LLM
 * (constitution VI; FR-008, FR-035). Every amount is {@link BigDecimal}; multiplication and addition
 * are exact and money is stored unscaled — the only rounding is the 12-decimal scale on the
 * non-terminating weight divisions, and 2-decimal display rounding happens in the UI (FR-031).
 *
 * <p>Algorithm and worked examples:
 * {@code specs/FD004-portfolio-valuation-and-allocation/contracts/valuation-calculation.md}.
 */
public final class PortfolioValuationCalculator {

    private static final String EUR = "EUR";
    private static final String USD = "USD";
    private static final int WEIGHT_SCALE = 12;

    /**
     * @param portfolioId  the Portfolio being valued
     * @param inputs       one entry per Portfolio Position, in Portfolio order
     * @param fx           the FX rates gathered for this run (only the needed directions)
     * @param calculatedAt the instant this snapshot is computed
     */
    public PortfolioValuation calculate(PortfolioId portfolioId, List<PositionInput> inputs,
                                        FxContext fx, Instant calculatedAt) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(fx, "fx");
        Objects.requireNonNull(calculatedAt, "calculatedAt");

        // Step 1 + 2 — per-Position native / EUR / USD value (no weights yet).
        List<PositionValuation> valuedNoWeight = new ArrayList<>();
        for (PositionInput in : inputs) {
            valuedNoWeight.add(valuePosition(in, fx));
        }

        // Step 3 — totals over the valued Positions.
        List<PositionValuation> valued = valuedNoWeight.stream().filter(PositionValuation::valued).toList();
        Optional<BigDecimal> totalEUR = total(valued, PositionValuation::valueInEUR);
        Optional<BigDecimal> totalUSD = total(valued, PositionValuation::valueInUSD);

        boolean eurBasis = totalEUR.isPresent() && totalEUR.get().signum() > 0;

        // Step 4 — weights + sector allocation (only on a positive EUR basis).
        List<PositionValuation> positions = eurBasis
                ? withWeights(valuedNoWeight, totalEUR.get())
                : valuedNoWeight;
        List<SectorAllocation> sectors = eurBasis
                ? sectorAllocation(positions, totalEUR.get())
                : List.of();

        // Step 5 — status.
        ValuationStatus status = status(inputs.size(), valued, totalEUR, totalUSD);

        // Step 6 — freshness.
        Optional<Instant> marketDataAsOf = valued.stream()
                .map(PositionValuation::priceObservedAt).filter(Optional::isPresent).map(Optional::get)
                .min(Comparator.naturalOrder());
        Optional<Instant> fxDataAsOf = fxDataAsOf(valued, fx);

        return new PortfolioValuation(portfolioId, status, calculatedAt,
                totalEUR, totalUSD, marketDataAsOf, fxDataAsOf, positions, sectors);
    }

    private static PositionValuation valuePosition(PositionInput in, FxContext fx) {
        String sector = in.sector().filter(s -> !s.isBlank()).orElse(SectorAllocation.UNCLASSIFIED);
        if (in.price().isEmpty()) {
            return PositionValuation.unvalued(in.ticker(), in.market(), in.quantity(),
                    in.nativeCurrency(), sector);
        }
        BigDecimal price = in.price().get();
        BigDecimal nativeValue = in.quantity().multiply(price);

        Optional<BigDecimal> valueEUR;
        Optional<BigDecimal> valueUSD;
        if (USD.equals(in.nativeCurrency())) {
            valueUSD = Optional.of(nativeValue);
            valueEUR = fx.usdToEur().map(nativeValue::multiply);
        } else {
            valueEUR = Optional.of(nativeValue);
            valueUSD = fx.eurToUsd().map(nativeValue::multiply);
        }
        return new PositionValuation(in.ticker(), in.market(), in.quantity(), in.nativeCurrency(),
                true, Optional.of(price), Optional.of(nativeValue), valueEUR, valueUSD,
                Optional.empty(), sector, in.priceObservedAt());
    }

    private static Optional<BigDecimal> total(List<PositionValuation> valued,
                                              java.util.function.Function<PositionValuation, Optional<BigDecimal>> side) {
        if (valued.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PositionValuation p : valued) {
            Optional<BigDecimal> v = side.apply(p);
            if (v.isEmpty()) {
                return Optional.empty();
            }
            sum = sum.add(v.get());
        }
        return Optional.of(sum);
    }

    private static List<PositionValuation> withWeights(List<PositionValuation> positions,
                                                      BigDecimal totalEUR) {
        List<PositionValuation> out = new ArrayList<>(positions.size());
        for (PositionValuation p : positions) {
            if (p.valued() && p.valueInEUR().isPresent()) {
                BigDecimal weight = p.valueInEUR().get().divide(totalEUR, WEIGHT_SCALE, RoundingMode.HALF_UP);
                out.add(new PositionValuation(p.ticker(), p.market(), p.quantity(), p.nativeCurrency(),
                        true, p.marketPrice(), p.nativeMarketValue(), p.valueInEUR(), p.valueInUSD(),
                        Optional.of(weight), p.sector(), p.priceObservedAt()));
            } else {
                out.add(p);
            }
        }
        return out;
    }

    private static List<SectorAllocation> sectorAllocation(List<PositionValuation> positions,
                                                           BigDecimal totalEUR) {
        Map<String, BigDecimal> bySector = new LinkedHashMap<>();
        for (PositionValuation p : positions) {
            if (p.valued() && p.valueInEUR().isPresent()) {
                bySector.merge(p.sector(), p.valueInEUR().get(), BigDecimal::add);
            }
        }
        List<SectorAllocation> out = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : bySector.entrySet()) {
            BigDecimal weight = e.getValue().divide(totalEUR, WEIGHT_SCALE, RoundingMode.HALF_UP);
            out.add(new SectorAllocation(e.getKey(), e.getValue(), weight));
        }
        out.sort(Comparator.comparing(SectorAllocation::sectorValueEUR).reversed()
                .thenComparing(SectorAllocation::sector));
        return List.copyOf(out);
    }

    private static ValuationStatus status(int positionCount, List<PositionValuation> valued,
                                          Optional<BigDecimal> totalEUR, Optional<BigDecimal> totalUSD) {
        if (valued.isEmpty()) {
            return ValuationStatus.FAILED;
        }
        if (totalEUR.isEmpty() && totalUSD.isEmpty()) {
            return ValuationStatus.FAILED;
        }
        boolean everyValuedHasBothSides = valued.stream()
                .allMatch(p -> p.valueInEUR().isPresent() && p.valueInUSD().isPresent());
        if (valued.size() == positionCount && everyValuedHasBothSides) {
            return ValuationStatus.COMPLETED;
        }
        return ValuationStatus.PARTIAL;
    }

    private static Optional<Instant> fxDataAsOf(List<PositionValuation> valued, FxContext fx) {
        boolean usedUsdToEur = valued.stream()
                .anyMatch(p -> USD.equals(p.nativeCurrency()) && p.valueInEUR().isPresent());
        boolean usedEurToUsd = valued.stream()
                .anyMatch(p -> EUR.equals(p.nativeCurrency()) && p.valueInUSD().isPresent());
        List<Instant> used = new ArrayList<>();
        if (usedUsdToEur) {
            fx.usdToEurObservedAt().ifPresent(used::add);
        }
        if (usedEurToUsd) {
            fx.eurToUsdObservedAt().ifPresent(used::add);
        }
        return used.stream().min(Comparator.naturalOrder());
    }
}
