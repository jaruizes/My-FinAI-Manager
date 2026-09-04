package com.myfinaimanager.core.portfolio.infrastructure.api.rest;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotSavedException;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioValidationException;
import com.myfinaimanager.core.portfolio.domain.model.Violation;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps portfolio failures to RFC 9457 {@code application/problem+json} (AR-012). Stable, machine
 * readable {@code type} identifiers; human-readable, non-technical messages; no stack traces, SQL,
 * or framework class names leak (DR-020).
 */
@RestControllerAdvice(assignableTypes = {
        CreatePortfolioController.class,
        PortfolioQueryController.class,
        PortfolioValuationController.class})
public class PortfolioExceptionHandler {

    @ExceptionHandler(PortfolioNotFoundException.class)
    public ProblemDetail onNotFound(PortfolioNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("/problems/portfolio-not-found"));
        problem.setTitle("Portfolio not found");
        problem.setDetail("No portfolio with that identifier exists.");
        problem.setInstance(URI.create("/api/portfolios/" + ex.portfolioId()));
        return problem;
    }

    @ExceptionHandler(PortfolioValidationException.class)
    public ProblemDetail onValidation(PortfolioValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("/problems/portfolio-validation"));
        problem.setTitle("Portfolio could not be validated");
        int n = ex.violations().size();
        problem.setDetail("The portfolio has " + n + (n == 1 ? " problem" : " problems")
                + " that need to be fixed.");
        problem.setProperty("errors", toErrors(ex.violations()));
        return problem;
    }

    @ExceptionHandler(PortfolioNotSavedException.class)
    public ProblemDetail onNotSaved(PortfolioNotSavedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create("/problems/portfolio-not-saved"));
        problem.setTitle("Portfolio could not be saved");
        problem.setDetail("We could not save your portfolio right now. Please try again.");
        return problem;
    }

    private static List<Map<String, String>> toErrors(List<Violation> violations) {
        return violations.stream()
                .map(v -> {
                    Map<String, String> e = new LinkedHashMap<>();
                    e.put("field", v.field());
                    e.put("code", v.code());
                    e.put("message", v.message());
                    return e;
                })
                .toList();
    }
}
