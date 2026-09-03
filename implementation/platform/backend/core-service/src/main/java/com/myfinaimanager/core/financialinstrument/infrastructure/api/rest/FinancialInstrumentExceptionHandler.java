package com.myfinaimanager.core.financialinstrument.infrastructure.api.rest;

import com.myfinaimanager.core.financialinstrument.domain.exceptions.InvalidSearchQueryException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@code GET /api/financial-instruments} failures to RFC 9457 {@code application/problem+json}
 * (AR-012). Stable {@code type}; friendly, non-technical detail; no stack traces / SQL / framework
 * names leak.
 */
@RestControllerAdvice(assignableTypes = FinancialInstrumentSearchController.class)
public class FinancialInstrumentExceptionHandler {

    @ExceptionHandler(InvalidSearchQueryException.class)
    public ProblemDetail onInvalidQuery(InvalidSearchQueryException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("/problems/invalid-search-query"));
        problem.setTitle("Search query is required");
        problem.setDetail("Provide a ticker or part of an instrument name to search for.");
        problem.setInstance(URI.create("/api/financial-instruments"));
        return problem;
    }
}
