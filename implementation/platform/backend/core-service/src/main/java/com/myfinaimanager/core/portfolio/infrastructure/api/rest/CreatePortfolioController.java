package com.myfinaimanager.core.portfolio.infrastructure.api.rest;

import com.myfinaimanager.core.portfolio.business.CreatePortfolioResult;
import com.myfinaimanager.core.portfolio.business.CreatePortfolioUseCase;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.CreatePortfolioRequestMapper;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.PortfolioResponseMapper;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.CreatePortfolioRequest;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.CreatePortfolioResponse;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter — implements {@code POST /api/portfolios} exactly as defined in
 * {@code implementation/platform/contracts/openapi/openapi.yaml}. Contains no business logic: it
 * maps the request to a business command (via {@link CreatePortfolioRequestMapper}), invokes the
 * use case, and maps the result back (via {@link PortfolioResponseMapper}). Validation failures
 * are turned into RFC 9457 problems by {@link PortfolioExceptionHandler}.
 */
@RestController
public class CreatePortfolioController {

    private final CreatePortfolioUseCase createPortfolio;
    private final CreatePortfolioRequestMapper requestMapper;
    private final PortfolioResponseMapper responseMapper;

    public CreatePortfolioController(CreatePortfolioUseCase createPortfolio,
                                     CreatePortfolioRequestMapper requestMapper,
                                     PortfolioResponseMapper responseMapper) {
        this.createPortfolio = createPortfolio;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    @PostMapping(
            path = "/api/portfolios",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreatePortfolioResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreatePortfolioRequest request) {

        CreatePortfolioResult result =
                createPortfolio.create(requestMapper.toCommand(request, idempotencyKey));
        CreatePortfolioResponse body = responseMapper.toResponse(result.portfolio());

        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotency-Replayed", "true")
                    .body(body);
        }
        return ResponseEntity
                .created(URI.create("/api/portfolios/" + body.id()))
                .body(body);
    }
}
