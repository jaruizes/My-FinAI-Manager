package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PositionId(UUID value) {
    public PositionId {
        Objects.requireNonNull(value, "position id");
    }
    public static PositionId newId() {
        return new PositionId(UUID.randomUUID());
    }
    public static PositionId of(UUID value) {
        return new PositionId(value);
    }
    @Override public String toString() {
        return value.toString();
    }
}
