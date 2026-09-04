package com.myfinaimanager.core.portfolio.infrastructure.valuation;

import com.myfinaimanager.core.portfolio.business.ValuePortfolioUseCase;
import com.myfinaimanager.core.portfolio.domain.events.PortfolioCreatedEvent;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioValuationRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs the FD004 valuation right after a Portfolio is created (FR-001, FR-004). The handler is a
 * <strong>synchronous</strong> {@link EventListener} — no {@code @Async}, no broker, no scheduler
 * (FR-003) — and it runs on the {@code POST /api/portfolios} request thread, after that request's
 * create transaction has already committed.
 *
 * <p>Everything is wrapped in a catch-all: a valuation failure is logged and a best-effort
 * {@code FAILED} snapshot is written, but it is <strong>never</strong> rethrown — the create
 * request still returns {@code 201} and the persisted Portfolio is untouched (FR-002; SC-005,
 * SC-008).
 */
@Component
public class PortfolioValuationOnCreationListener {

    private static final Logger log = LoggerFactory.getLogger(PortfolioValuationOnCreationListener.class);

    private final ValuePortfolioUseCase valuePortfolio;
    private final PortfolioValuationRepository valuations;
    private final Clock clock;

    public PortfolioValuationOnCreationListener(ValuePortfolioUseCase valuePortfolio,
                                                PortfolioValuationRepository valuations,
                                                Clock clock) {
        this.valuePortfolio = valuePortfolio;
        this.valuations = valuations;
        this.clock = clock;
    }

    @EventListener
    public void onPortfolioCreated(PortfolioCreatedEvent event) {
        PortfolioId portfolioId = event.portfolioId();
        try {
            valuePortfolio.value(portfolioId);
        } catch (RuntimeException e) {
            log.atWarn()
                    .addKeyValue("event", "PortfolioValuationFailed")
                    .addKeyValue("portfolioId", portfolioId.toString())
                    .addKeyValue("reason", e.getClass().getSimpleName())
                    .log("post-creation valuation failed; the portfolio is unaffected");
            writeFailedSnapshot(portfolioId);
        }
    }

    private void writeFailedSnapshot(PortfolioId portfolioId) {
        try {
            valuations.upsertLatest(new PortfolioValuation(portfolioId, ValuationStatus.FAILED,
                    clock.instant(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), List.of(), List.of()));
        } catch (RuntimeException e) {
            log.atError()
                    .addKeyValue("event", "PortfolioValuationSnapshotNotWritten")
                    .addKeyValue("portfolioId", portfolioId.toString())
                    .addKeyValue("reason", e.getClass().getSimpleName())
                    .log("could not persist a FAILED valuation snapshot");
        }
    }
}
