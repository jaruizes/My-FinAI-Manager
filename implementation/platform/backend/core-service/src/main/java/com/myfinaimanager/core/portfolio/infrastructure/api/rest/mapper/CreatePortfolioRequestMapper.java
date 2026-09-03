package com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper;

import com.myfinaimanager.core.portfolio.business.CreatePortfolioCommand;
import com.myfinaimanager.core.portfolio.domain.model.NewPosition;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.CreatePortfolioRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps the REST {@link CreatePortfolioRequest} to a business {@link CreatePortfolioCommand}.
 * Passes every value through verbatim — the domain does the parsing and validation (research.md D2).
 */
@Component
public class CreatePortfolioRequestMapper {

    public CreatePortfolioCommand toCommand(CreatePortfolioRequest request, String idempotencyKey) {
        List<NewPosition> positions = request.positions() == null ? List.of()
                : request.positions().stream()
                        .map(p -> new NewPosition(
                                p.ticker(), p.market(), p.quantity(), p.currency(),
                                p.initialPurchaseDate(), p.averagePurchasePrice()))
                        .toList();
        return new CreatePortfolioCommand(request.name(), positions, idempotencyKey);
    }
}
