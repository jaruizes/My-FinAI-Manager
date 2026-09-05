package com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myfinaimanager.core.portfolioanalysis.business.PortfolioAnalysisQueryUseCase;
import com.myfinaimanager.core.portfolioanalysis.business.RequestPortfolioAnalysisUseCase;
import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto.PortfolioAnalysisResponse;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto.RequestedPortfolioAnalysisResponse;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.mapper.PortfolioAnalysisResponseMapper;

/**
 * Inbound REST adapter for FD005, exactly as defined in
 * {@code implementation/platform/contracts/openapi/openapi.yaml} (contract D8). No business logic
 * — delegates to {@link PortfolioAnalysisQueryUseCase} and maps. The {@code {portfolioId}} path
 * variable is typed {@link UUID}: a non-UUID segment yields Spring's default {@code 400}. A
 * well-formed but unknown id yields {@code 404} (via {@link PortfolioAnalysisExceptionHandler}). A
 * Portfolio with no analysis ever requested yields {@code 200} with an explicit {@code NONE} body.
 *
 * <p>{@code POST .../analysis} (US4) creates a new manual analysis request and returns {@code 202}
 * promptly — never waits for the AI call. Rejected with {@code 409} when the latest analysis for
 * this Portfolio is already {@code PENDING}/{@code RUNNING} (via {@link
 * PortfolioAnalysisExceptionHandler}).
 */
@RestController
public class PortfolioAnalysisController {

    private final PortfolioAnalysisQueryUseCase queries;
    private final RequestPortfolioAnalysisUseCase requests;
    private final PortfolioAnalysisResponseMapper mapper;

    public PortfolioAnalysisController(PortfolioAnalysisQueryUseCase queries,
                                       RequestPortfolioAnalysisUseCase requests,
                                       PortfolioAnalysisResponseMapper mapper) {
        this.queries = queries;
        this.requests = requests;
        this.mapper = mapper;
    }

    @GetMapping(path = "/api/portfolios/{portfolioId}/analysis/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public PortfolioAnalysisResponse getLatest(@PathVariable UUID portfolioId) {
        return mapper.toResponse(queries.findLatest(portfolioId));
    }

    /**
     * @throws AnalysisAlreadyInProgressException mapped to {@code 409} — the latest analysis for
     *                                             this portfolio is still {@code PENDING}/{@code RUNNING}
     */
    @PostMapping(path = "/api/portfolios/{portfolioId}/analysis", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequestedPortfolioAnalysisResponse> requestNew(@PathVariable UUID portfolioId) {
        PortfolioAnalysis requested = requests.requestManual(portfolioId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toRequested(requested));
    }
}
