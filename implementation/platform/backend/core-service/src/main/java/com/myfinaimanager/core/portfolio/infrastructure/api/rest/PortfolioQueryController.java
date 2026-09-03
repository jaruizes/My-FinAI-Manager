package com.myfinaimanager.core.portfolio.infrastructure.api.rest;

import com.myfinaimanager.core.portfolio.business.PortfolioQueryUseCase;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.CreatePortfolioResponse;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.PortfolioSummaryResponse;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.PortfolioResponseMapper;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.PortfolioSummaryMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for the FD003 read operations, exactly as defined in
 * {@code implementation/platform/contracts/openapi/openapi.yaml}:
 * {@code GET /api/portfolios} (the current investor's portfolios, newest first) and
 * {@code GET /api/portfolios/{portfolioId}} (one portfolio with its positions). Separate from
 * {@link CreatePortfolioController} (POST) so each controller stays single-purpose.
 *
 * <p>Contains no business logic — it delegates to {@link PortfolioQueryUseCase} and maps. The
 * {@code {portfolioId}} path variable is typed {@link UUID}: a non-UUID segment yields Spring's
 * default {@code 400} before the use case is reached, distinct from the {@code 404}
 * ({@code PortfolioNotFoundException} via {@link PortfolioExceptionHandler}) for a well-formed but
 * unknown id.
 */
@RestController
public class PortfolioQueryController {

    private final PortfolioQueryUseCase portfolios;
    private final PortfolioSummaryMapper summaryMapper;
    private final PortfolioResponseMapper responseMapper;

    public PortfolioQueryController(PortfolioQueryUseCase portfolios,
                                    PortfolioSummaryMapper summaryMapper,
                                    PortfolioResponseMapper responseMapper) {
        this.portfolios = portfolios;
        this.summaryMapper = summaryMapper;
        this.responseMapper = responseMapper;
    }

    @GetMapping(path = "/api/portfolios", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PortfolioSummaryResponse> list() {
        return portfolios.list().stream().map(summaryMapper::toResponse).toList();
    }

    @GetMapping(path = "/api/portfolios/{portfolioId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreatePortfolioResponse get(@PathVariable UUID portfolioId) {
        return responseMapper.toResponse(portfolios.view(PortfolioId.of(portfolioId)));
    }
}
