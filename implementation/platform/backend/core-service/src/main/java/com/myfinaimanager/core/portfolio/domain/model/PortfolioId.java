package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PortfolioId(UUID value) {
    public PortfolioId {
        Objects.requireNonNull(value, "portfolio id");
    }
    public static PortfolioId newId() {
        return new PortfolioId(UUID.randomUUID());
    }
    public static PortfolioId of(UUID value) {
        return new PortfolioId(value);
    }
    @Override public String toString() {
        return value.toString();
    }
}
