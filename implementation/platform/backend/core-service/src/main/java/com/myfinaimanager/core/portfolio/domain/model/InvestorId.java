package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Objects;
import java.util.UUID;

public record InvestorId(UUID value) {
    public InvestorId {
        Objects.requireNonNull(value, "investor id");
    }
    public static InvestorId of(UUID value) {
        return new InvestorId(value);
    }
    @Override public String toString() {
        return value.toString();
    }
}
