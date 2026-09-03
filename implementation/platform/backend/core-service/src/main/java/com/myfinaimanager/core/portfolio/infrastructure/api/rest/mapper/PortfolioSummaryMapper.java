package com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper;

import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.PortfolioSummaryResponse;
import org.springframework.stereotype.Component;

/**
 * Maps the domain {@link Portfolio} aggregate to the REST {@link PortfolioSummaryResponse} shown in
 * the Home list. The position count is the size of the loaded positions collection (FR-024) — no
 * denormalised counter. Nothing else about the aggregate is exposed here.
 */
@Component
public class PortfolioSummaryMapper {

    public PortfolioSummaryResponse toResponse(Portfolio portfolio) {
        return new PortfolioSummaryResponse(
                portfolio.id().toString(),
                portfolio.name().value(),
                portfolio.positions().size());
    }
}
