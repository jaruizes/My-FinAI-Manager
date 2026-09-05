package com.myfinaimanager.core.portfolioanalysis.infrastructure.portfolio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.myfinaimanager.core.portfolio.domain.events.PortfolioCreatedEvent;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolioanalysis.business.RequestPortfolioAnalysisUseCase;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalysisOnCreationListenerTest {

    private static final UUID PORTFOLIO_UUID = UUID.randomUUID();
    private static final PortfolioId PORTFOLIO_ID = PortfolioId.of(PORTFOLIO_UUID);

    @Mock private RequestPortfolioAnalysisUseCase requests;

    private PortfolioAnalysisOnCreationListener listener() {
        return new PortfolioAnalysisOnCreationListener(requests);
    }

    @Test
    void delegates_to_the_use_case_on_the_happy_path() {
        when(requests.requestAutomatic(PORTFOLIO_UUID)).thenReturn(
                PortfolioAnalysis.requested(PORTFOLIO_UUID, Instant.now(), CreationTrigger.AUTOMATIC));

        listener().onPortfolioCreated(new PortfolioCreatedEvent(PORTFOLIO_ID));

        verify(requests).requestAutomatic(PORTFOLIO_UUID);
    }

    @Test
    void a_request_failure_is_swallowed_never_rethrown() {
        doThrow(new RuntimeException("boom")).when(requests).requestAutomatic(any());

        listener().onPortfolioCreated(new PortfolioCreatedEvent(PORTFOLIO_ID)); // must not throw

        verify(requests).requestAutomatic(PORTFOLIO_UUID);
    }
}
