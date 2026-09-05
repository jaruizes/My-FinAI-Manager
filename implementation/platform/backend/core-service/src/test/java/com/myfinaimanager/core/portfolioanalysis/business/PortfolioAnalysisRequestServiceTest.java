package com.myfinaimanager.core.portfolioanalysis.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioValuationStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisRepository;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioContextGateway;

/** requestAutomatic/requestManual create a PENDING row and trigger the worker exactly once; duplicate rejection. */
class PortfolioAnalysisRequestServiceTest {

    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-05T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static PortfolioContextSnapshot snapshot() {
        return new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.ABSENT, Optional.empty(), Optional.empty(), java.util.List.of(),
                java.util.List.of(), Optional.empty());
    }

    @Test
    void requestAutomatic_saves_a_pending_analysis_and_triggers_the_worker_exactly_once() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisWorker worker = mock(PortfolioAnalysisWorker.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PortfolioAnalysisRequestService service =
                new PortfolioAnalysisRequestService(repository, contextGateway, worker, CLOCK);

        PortfolioAnalysis result = service.requestAutomatic(PORTFOLIO_ID);

        assertThat(result.status()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(result.portfolioId()).isEqualTo(PORTFOLIO_ID);
        assertThat(result.createdByTrigger()).isEqualTo(CreationTrigger.AUTOMATIC);
        assertThat(result.requestedAt()).isEqualTo(NOW);
        verify(worker, times(1)).runAsync(result.id());
        verify(contextGateway, never()).fetch(any()); // no existence check for the automatic path
    }

    @Test
    void requestManual_validates_portfolio_existence_then_saves_and_triggers_the_worker_once() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisWorker worker = mock(PortfolioAnalysisWorker.class);
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(snapshot());
        when(repository.findLatestByPortfolioId(PORTFOLIO_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PortfolioAnalysisRequestService service =
                new PortfolioAnalysisRequestService(repository, contextGateway, worker, CLOCK);

        PortfolioAnalysis result = service.requestManual(PORTFOLIO_ID);

        assertThat(result.createdByTrigger()).isEqualTo(CreationTrigger.MANUAL);
        verify(contextGateway, times(1)).fetch(PORTFOLIO_ID);
        verify(worker, times(1)).runAsync(result.id());
    }

    @Test
    void requestManual_rejects_when_the_latest_analysis_is_pending() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisWorker worker = mock(PortfolioAnalysisWorker.class);
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(snapshot());
        PortfolioAnalysis existingPending =
                PortfolioAnalysis.requested(PORTFOLIO_ID, NOW.minusSeconds(5), CreationTrigger.AUTOMATIC);
        when(repository.findLatestByPortfolioId(PORTFOLIO_ID)).thenReturn(Optional.of(existingPending));
        PortfolioAnalysisRequestService service =
                new PortfolioAnalysisRequestService(repository, contextGateway, worker, CLOCK);

        assertThatThrownBy(() -> service.requestManual(PORTFOLIO_ID))
                .isInstanceOf(AnalysisAlreadyInProgressException.class);
        verify(worker, never()).runAsync(any());
    }

    @Test
    void requestManual_rejects_when_the_latest_analysis_is_running() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisWorker worker = mock(PortfolioAnalysisWorker.class);
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(snapshot());
        PortfolioAnalysis existingRunning = PortfolioAnalysis
                .requested(PORTFOLIO_ID, NOW.minusSeconds(5), CreationTrigger.MANUAL)
                .withRunning(NOW.minusSeconds(1));
        when(repository.findLatestByPortfolioId(PORTFOLIO_ID)).thenReturn(Optional.of(existingRunning));
        PortfolioAnalysisRequestService service =
                new PortfolioAnalysisRequestService(repository, contextGateway, worker, CLOCK);

        assertThatThrownBy(() -> service.requestManual(PORTFOLIO_ID))
                .isInstanceOf(AnalysisAlreadyInProgressException.class);
    }

    @Test
    void requestManual_allows_a_new_request_when_the_latest_is_terminal() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisWorker worker = mock(PortfolioAnalysisWorker.class);
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(snapshot());
        PortfolioAnalysis completed = PortfolioAnalysis
                .requested(PORTFOLIO_ID, NOW.minusSeconds(60), CreationTrigger.AUTOMATIC)
                .withFailed(com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason.UNKNOWN,
                        NOW.minusSeconds(30));
        when(repository.findLatestByPortfolioId(PORTFOLIO_ID)).thenReturn(Optional.of(completed));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PortfolioAnalysisRequestService service =
                new PortfolioAnalysisRequestService(repository, contextGateway, worker, CLOCK);

        PortfolioAnalysis result = service.requestManual(PORTFOLIO_ID);

        assertThat(result.status()).isEqualTo(AnalysisStatus.PENDING);
        verify(worker, times(1)).runAsync(result.id());
    }

    @Test
    void requestManual_propagates_portfolio_not_found_from_the_existence_check() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisWorker worker = mock(PortfolioAnalysisWorker.class);
        when(contextGateway.fetch(PORTFOLIO_ID)).thenThrow(new RuntimeException("not found stand-in"));
        PortfolioAnalysisRequestService service =
                new PortfolioAnalysisRequestService(repository, contextGateway, worker, CLOCK);

        assertThatThrownBy(() -> service.requestManual(PORTFOLIO_ID)).isInstanceOf(RuntimeException.class);
        verify(repository, never()).save(any());
        verify(worker, never()).runAsync(any());
    }
}
