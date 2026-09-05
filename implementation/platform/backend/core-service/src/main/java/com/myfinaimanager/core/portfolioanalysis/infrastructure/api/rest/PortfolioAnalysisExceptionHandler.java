package com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.PortfolioNotFoundException;

/**
 * Maps FD005 failures to RFC 9457 {@code application/problem+json} (AR-012) — scoped to {@link
 * PortfolioAnalysisController} only, own type declared here rather than widening {@code
 * portfolio.infrastructure.api.rest.PortfolioExceptionHandler} (that would make the {@code
 * portfolio} module depend on {@code portfolioanalysis} — backwards; AR-062). Both exception types
 * handled here are {@code portfolioanalysis}'s own — {@link PortfolioNotFoundException} is
 * translated from FD003's exception at the ACL boundary (see its javadoc), so the public HTTP
 * shape is identical to FD003/FD004's own 404 either way.
 */
@RestControllerAdvice(assignableTypes = PortfolioAnalysisController.class)
public class PortfolioAnalysisExceptionHandler {

    @ExceptionHandler(PortfolioNotFoundException.class)
    public ProblemDetail onPortfolioNotFound(PortfolioNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("/problems/portfolio-not-found"));
        problem.setTitle("Portfolio not found");
        problem.setDetail("No portfolio with that identifier exists.");
        problem.setInstance(URI.create("/api/portfolios/" + ex.portfolioId()));
        return problem;
    }

    @ExceptionHandler(AnalysisAlreadyInProgressException.class)
    public ProblemDetail onAlreadyInProgress(AnalysisAlreadyInProgressException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("/problems/analysis-already-in-progress"));
        problem.setTitle("Analysis already in progress");
        problem.setDetail("A Portfolio Analysis is already pending or running for this portfolio.");
        return problem;
    }
}
