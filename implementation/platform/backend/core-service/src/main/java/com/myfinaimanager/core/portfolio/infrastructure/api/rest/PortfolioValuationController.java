package com.myfinaimanager.core.portfolio.infrastructure.api.rest;

import com.myfinaimanager.core.portfolio.business.PortfolioValuationQueryUseCase;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.PortfolioValuationResponse;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.PortfolioValuationResponseMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for the FD004 valuation read operation
 * {@code GET /api/portfolios/{portfolioId}/valuation}, exactly as defined in
 * {@code implementation/platform/contracts/openapi/openapi.yaml}. Separate from
 * {@link PortfolioQueryController} so the FD003 read concern and the FD004 valuation concern stay
 * single-purpose; the FD003 {@code Portfolio} contract is unchanged.
 *
 * <p>No business logic — delegates to {@link PortfolioValuationQueryUseCase} and maps. The
 * {@code {portfolioId}} path variable is typed {@link UUID}: a non-UUID segment yields Spring's
 * default {@code 400}. A well-formed but unknown / not-the-current-investor's id yields {@code 404}
 * ({@code PortfolioNotFoundException} via {@link PortfolioExceptionHandler}). A Portfolio that
 * exists but has no snapshot yet yields {@code 200} with an explicit {@code PENDING} body (FR-025).
 */
@RestController
public class PortfolioValuationController {

    private final PortfolioValuationQueryUseCase valuations;
    private final PortfolioValuationResponseMapper mapper;

    public PortfolioValuationController(PortfolioValuationQueryUseCase valuations,
                                        PortfolioValuationResponseMapper mapper) {
        this.valuations = valuations;
        this.mapper = mapper;
    }

    @GetMapping(path = "/api/portfolios/{portfolioId}/valuation", produces = MediaType.APPLICATION_JSON_VALUE)
    public PortfolioValuationResponse get(@PathVariable UUID portfolioId) {
        PortfolioId id = PortfolioId.of(portfolioId);
        return valuations.findLatest(id)
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.pending(id));
    }
}
