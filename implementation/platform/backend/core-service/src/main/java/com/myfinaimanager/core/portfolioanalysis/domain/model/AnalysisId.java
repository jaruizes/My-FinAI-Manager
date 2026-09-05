package com.myfinaimanager.core.portfolioanalysis.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of one {@link PortfolioAnalysis} request (FD005 data-model.md §2). */
public record AnalysisId(UUID value) {

    public AnalysisId {
        Objects.requireNonNull(value, "analysis id");
    }

    public static AnalysisId newId() {
        return new AnalysisId(UUID.randomUUID());
    }

    public static AnalysisId of(UUID value) {
        return new AnalysisId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
