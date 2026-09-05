package com.myfinaimanager.core.portfolioanalysis.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisFailedException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.DiversificationLevel;
import com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioValuationStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisAiPort;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisRepository;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioContextGateway;

/**
 * Happy path RUNNING→COMPLETED; every {@link AnalysisFailedException} reason → FAILED; an
 * unexpected {@link RuntimeException} mid-flow → FAILED(UNKNOWN) (never stuck at RUNNING, FR-057);
 * an insufficient-data snapshot → FAILED(INSUFFICIENT_DATA) with zero calls to the AI port.
 */
class PortfolioAnalysisWorkerTest {

    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-05T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static PortfolioAnalysis pending() {
        return PortfolioAnalysis.requested(PORTFOLIO_ID, NOW.minusSeconds(1), CreationTrigger.AUTOMATIC);
    }

    private static PortfolioContextSnapshot sufficientSnapshot() {
        return new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.COMPLETED, Optional.of(BigDecimal.TEN), Optional.empty(),
                List.of(new PortfolioContextSnapshot.PositionSnapshot(
                        "AAPL", Optional.of(BigDecimal.ONE), Optional.of(BigDecimal.TEN),
                        Optional.of("Technology"), "USD")),
                List.of(), Optional.of(NOW));
    }

    private static PortfolioContextSnapshot insufficientSnapshot() {
        return new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.ABSENT, Optional.empty(), Optional.empty(), List.of(), List.of(),
                Optional.empty());
    }

    private static PortfolioAnalysisResult result() {
        return new PortfolioAnalysisResult(
                DiversificationLevel.HIGH, "Well diversified.", List.of(), List.of(), "openai", "gpt-4o-mini",
                "portfolio-analysis", "v1", 10, 5, 15, new BigDecimal("0.001"));
    }

    private static PortfolioAnalysis capturedSave(PortfolioAnalysisRepository repository, int callIndex) {
        ArgumentCaptor<PortfolioAnalysis> captor = ArgumentCaptor.forClass(PortfolioAnalysis.class);
        verify(repository, org.mockito.Mockito.atLeast(callIndex + 1)).save(captor.capture());
        return captor.getAllValues().get(callIndex);
    }

    @Test
    void happy_path_transitions_pending_to_running_to_completed() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisAiPort aiPort = mock(PortfolioAnalysisAiPort.class);
        PortfolioAnalysis pending = pending();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(sufficientSnapshot());
        when(aiPort.analyze(any(), any())).thenReturn(result());
        PortfolioAnalysisWorker worker = new PortfolioAnalysisWorker(repository, contextGateway, aiPort, CLOCK);

        worker.runAsync(pending.id());

        PortfolioAnalysis runningSave = capturedSave(repository, 0);
        assertThat(runningSave.status()).isEqualTo(AnalysisStatus.RUNNING);
        PortfolioAnalysis completedSave = capturedSave(repository, 1);
        assertThat(completedSave.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completedSave.overallDiversification()).contains(DiversificationLevel.HIGH);
    }

    @Test
    void an_analysis_failed_exception_transitions_to_failed_with_its_reason() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisAiPort aiPort = mock(PortfolioAnalysisAiPort.class);
        PortfolioAnalysis pending = pending();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(sufficientSnapshot());
        when(aiPort.analyze(any(), any()))
                .thenThrow(new AnalysisFailedException(FailureReason.PROVIDER_UNAVAILABLE, "down"));
        PortfolioAnalysisWorker worker = new PortfolioAnalysisWorker(repository, contextGateway, aiPort, CLOCK);

        worker.runAsync(pending.id());

        PortfolioAnalysis failedSave = capturedSave(repository, 1);
        assertThat(failedSave.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failedSave.failureReasonCode()).contains(FailureReason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void an_unexpected_runtime_exception_mid_flow_transitions_to_failed_unknown_never_stuck_running() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisAiPort aiPort = mock(PortfolioAnalysisAiPort.class);
        PortfolioAnalysis pending = pending();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contextGateway.fetch(PORTFOLIO_ID)).thenThrow(new IllegalStateException("boom"));
        PortfolioAnalysisWorker worker = new PortfolioAnalysisWorker(repository, contextGateway, aiPort, CLOCK);

        worker.runAsync(pending.id());

        PortfolioAnalysis failedSave = capturedSave(repository, 1);
        assertThat(failedSave.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failedSave.failureReasonCode()).contains(FailureReason.UNKNOWN);
        verifyNoInteractions(aiPort);
    }

    @Test
    void an_insufficient_snapshot_fails_as_insufficient_data_with_zero_ai_port_calls() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisAiPort aiPort = mock(PortfolioAnalysisAiPort.class);
        PortfolioAnalysis pending = pending();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(insufficientSnapshot());
        PortfolioAnalysisWorker worker = new PortfolioAnalysisWorker(repository, contextGateway, aiPort, CLOCK);

        worker.runAsync(pending.id());

        PortfolioAnalysis failedSave = capturedSave(repository, 1);
        assertThat(failedSave.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failedSave.failureReasonCode()).contains(FailureReason.INSUFFICIENT_DATA);
        verifyNoInteractions(aiPort);
    }

    @Test
    void a_missing_analysis_row_is_logged_and_does_nothing_further() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        PortfolioAnalysisAiPort aiPort = mock(PortfolioAnalysisAiPort.class);
        com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisId missingId =
                com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisId.newId();
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        PortfolioAnalysisWorker worker = new PortfolioAnalysisWorker(repository, contextGateway, aiPort, CLOCK);

        worker.runAsync(missingId);

        verify(repository, never()).save(any());
        verifyNoInteractions(contextGateway, aiPort);
    }
}
