package com.myfinaimanager.core.portfolioanalysis.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioValuationStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisRepository;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioContextGateway;

/** findLatest returns empty ("NONE") when absent, else the latest row regardless of status. */
class PortfolioAnalysisQueryServiceTest {

    private static final UUID PORTFOLIO_ID = UUID.randomUUID();

    private static PortfolioContextSnapshot snapshot() {
        return new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.ABSENT, Optional.empty(), Optional.empty(), List.of(), List.of(),
                Optional.empty());
    }

    @Test
    void returns_empty_when_no_analysis_has_ever_been_requested() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(snapshot());
        when(repository.findLatestByPortfolioId(PORTFOLIO_ID)).thenReturn(Optional.empty());
        PortfolioAnalysisQueryService service = new PortfolioAnalysisQueryService(repository, contextGateway);

        assertThat(service.findLatest(PORTFOLIO_ID)).isEmpty();
    }

    @Test
    void returns_the_latest_row_regardless_of_status() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        when(contextGateway.fetch(PORTFOLIO_ID)).thenReturn(snapshot());
        PortfolioAnalysis running = PortfolioAnalysis
                .requested(PORTFOLIO_ID, Instant.parse("2026-09-05T09:00:00Z"), CreationTrigger.AUTOMATIC)
                .withRunning(Instant.parse("2026-09-05T09:00:01Z"));
        when(repository.findLatestByPortfolioId(PORTFOLIO_ID)).thenReturn(Optional.of(running));
        PortfolioAnalysisQueryService service = new PortfolioAnalysisQueryService(repository, contextGateway);

        Optional<PortfolioAnalysis> result = service.findLatest(PORTFOLIO_ID);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(AnalysisStatus.RUNNING);
    }

    @Test
    void validates_portfolio_existence_before_reading_the_repository() {
        PortfolioAnalysisRepository repository = mock(PortfolioAnalysisRepository.class);
        PortfolioContextGateway contextGateway = mock(PortfolioContextGateway.class);
        when(contextGateway.fetch(PORTFOLIO_ID)).thenThrow(new RuntimeException("not found stand-in"));
        PortfolioAnalysisQueryService service = new PortfolioAnalysisQueryService(repository, contextGateway);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findLatest(PORTFOLIO_ID))
                .isInstanceOf(RuntimeException.class);
        verify(repository, org.mockito.Mockito.never()).findLatestByPortfolioId(any());
    }
}
