package com.myfinaimanager.core.portfolioanalysis.infrastructure.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.myfinaimanager.core.portfolio.domain.events.PortfolioCreatedEvent;
import com.myfinaimanager.core.portfolioanalysis.business.RequestPortfolioAnalysisUseCase;

/**
 * Triggers an automatic analysis right after a Portfolio is created (US1; research D5) by reusing
 * FD004's existing {@link PortfolioCreatedEvent} — a second, independent {@code @EventListener}
 * alongside FD004's own {@code PortfolioValuationOnCreationListener}; neither's failure affects the
 * other.
 *
 * <p>Placed in {@code infrastructure.portfolio} (the same ACL package as
 * {@code PortfolioContextGatewayAdapter}), not {@code portfolioanalysis.business} — this is the only
 * class allowed to import {@code portfolio.domain.events.PortfolioCreatedEvent} (AR-062; the
 * "implementation correction" note in data-model.md applies the same reasoning here).
 *
 * <p>The listener is a <strong>synchronous</strong> {@code @EventListener} — it runs on the create
 * request's thread, after that transaction has already committed (mirrors FD004's own listener).
 * It only creates the {@code PENDING} row and hands off to the async worker (FR-056) — the actual
 * analysis work never runs on this thread. Any failure here is logged and swallowed, never
 * rethrown — the create request still returns normally (mirrors
 * {@code PortfolioValuationOnCreationListener}'s catch-all pattern).
 */
@Component
public class PortfolioAnalysisOnCreationListener {

    private static final Logger log = LoggerFactory.getLogger(PortfolioAnalysisOnCreationListener.class);

    private final RequestPortfolioAnalysisUseCase requestPortfolioAnalysis;

    public PortfolioAnalysisOnCreationListener(RequestPortfolioAnalysisUseCase requestPortfolioAnalysis) {
        this.requestPortfolioAnalysis = requestPortfolioAnalysis;
    }

    @EventListener
    public void onPortfolioCreated(PortfolioCreatedEvent event) {
        try {
            requestPortfolioAnalysis.requestAutomatic(event.portfolioId().value());
        } catch (RuntimeException e) {
            log.atWarn()
                    .addKeyValue("event", "PortfolioAnalysisRequestFailed")
                    .addKeyValue("portfolioId", event.portfolioId().toString())
                    .addKeyValue("reason", e.getClass().getSimpleName())
                    .log("automatic post-creation analysis request failed; the portfolio is unaffected");
        }
    }
}
