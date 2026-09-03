package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.model.NewPosition;
import java.util.List;
import java.util.Objects;

/**
 * Request to create one portfolio. Carries the investor's raw input verbatim — the domain does
 * the parsing and validation (research.md D2).
 *
 * @param name           raw portfolio name (may be blank/too long — the domain decides)
 * @param positions      raw position inputs (may be empty)
 * @param idempotencyKey client-generated key for this creation attempt (FR-031a)
 */
public record CreatePortfolioCommand(String name, List<NewPosition> positions, String idempotencyKey) {

    public CreatePortfolioCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        positions = positions == null ? List.of() : List.copyOf(positions);
    }
}
