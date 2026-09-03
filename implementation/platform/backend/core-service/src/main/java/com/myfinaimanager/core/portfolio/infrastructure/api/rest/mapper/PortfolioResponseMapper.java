package com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper;

import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.CreatePortfolioResponse;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.CreatePortfolioResponse.PositionResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps the domain {@link Portfolio} aggregate to the REST {@link CreatePortfolioResponse}.
 * Decimal values are emitted as plain strings, exactly as stored (no precision loss); a missing
 * optional is {@code null} (never {@code 0}, never omitted). This formatting is the OpenAPI
 * contract — the contract test guards it.
 */
@Component
public class PortfolioResponseMapper {

    public CreatePortfolioResponse toResponse(Portfolio p) {
        List<PositionResponse> positions = p.positions().stream()
                .map(pos -> new PositionResponse(
                        pos.id().toString(),
                        pos.instrument().ticker().value(),
                        pos.instrument().market().value(),
                        pos.quantity().value().toPlainString(),
                        pos.currency().code(),
                        pos.initialPurchaseDate().map(LocalDate::toString).orElse(null),
                        pos.averagePurchasePrice().map(m -> m.amount().toPlainString()).orElse(null)))
                .toList();
        return new CreatePortfolioResponse(
                p.id().toString(),
                p.name().value(),
                p.status().name(),
                positions,
                p.createdAt().toString());
    }
}
