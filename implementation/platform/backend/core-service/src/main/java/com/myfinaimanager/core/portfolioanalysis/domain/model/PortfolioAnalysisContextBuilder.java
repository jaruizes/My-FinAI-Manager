package com.myfinaimanager.core.portfolioanalysis.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Pure calculator (no ports — mirrors {@code portfolio.domain.model.PortfolioValuationCalculator}
 * / {@code ai.domain.model.StructuredOutputValidator}): renders a {@link PortfolioContextSnapshot}
 * into the compact, deterministic prompt text an AI provider receives (research D6). Every output
 * is a pure function of its input — no port, no I/O, no randomness.
 */
public final class PortfolioAnalysisContextBuilder {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PortfolioAnalysisContextBuilder() {
    }

    public static PortfolioAnalysisContext build(PortfolioContextSnapshot snapshot) {
        return new PortfolioAnalysisContext(renderText(snapshot), isSufficient(snapshot));
    }

    /**
     * Insufficient (no provider call — FR-023, data-model.md §5) when the valuation never
     * completed at all (FAILED/PENDING/ABSENT), or completed/partial but valued no position.
     */
    private static boolean isSufficient(PortfolioContextSnapshot snapshot) {
        if (snapshot.valuationStatus() == PortfolioValuationStatus.FAILED
                || snapshot.valuationStatus() == PortfolioValuationStatus.PENDING
                || snapshot.valuationStatus() == PortfolioValuationStatus.ABSENT) {
            return false;
        }
        return snapshot.positions().stream().anyMatch(p -> p.valueEur().isPresent());
    }

    private static String renderText(PortfolioContextSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Portfolio \"").append(snapshot.portfolioName()).append("\" — valuation ")
                .append(snapshot.valuationStatus())
                .append(snapshot.valuedAt().map(v -> " as of " + v).orElse(""))
                .append(".\n");

        if (snapshot.valuationStatus() == PortfolioValuationStatus.PARTIAL) {
            long unvalued = snapshot.positions().stream().filter(p -> p.valueEur().isEmpty()).count();
            sb.append("Note: valuation is PARTIAL — ").append(unvalued)
                    .append(" position(s) could not be valued.\n");
        }

        if (snapshot.totalValueEur().isPresent() || snapshot.totalValueUsd().isPresent()) {
            sb.append("Total value: ")
                    .append(snapshot.totalValueEur().map(v -> "€" + money(v)).orElse("(n/a)"))
                    .append(" / ")
                    .append(snapshot.totalValueUsd().map(v -> "$" + money(v)).orElse("(n/a)"))
                    .append(".\n");
        }

        List<PortfolioContextSnapshot.PositionSnapshot> valuedPositions = snapshot.positions().stream()
                .filter(p -> p.weight().isPresent())
                .sorted(Comparator.comparing(
                        (PortfolioContextSnapshot.PositionSnapshot p) -> p.weight().orElseThrow()).reversed())
                .toList();
        if (!valuedPositions.isEmpty()) {
            sb.append("Positions (by weight):\n");
            for (PortfolioContextSnapshot.PositionSnapshot p : valuedPositions) {
                sb.append("- ").append(p.ticker()).append(": ").append(percent(p.weight().orElseThrow()))
                        .append(" (").append(p.sector().orElse("Unclassified"))
                        .append(p.valueEur().map(v -> ", €" + money(v)).orElse(""))
                        .append(")\n");
            }
        }

        if (!snapshot.sectors().isEmpty()) {
            sb.append("Sector allocation:\n");
            for (PortfolioContextSnapshot.SectorSnapshot s : snapshot.sectors()) {
                sb.append("- ").append(s.sector()).append(": ").append(percent(s.weight())).append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percent(BigDecimal fraction) {
        return fraction.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
