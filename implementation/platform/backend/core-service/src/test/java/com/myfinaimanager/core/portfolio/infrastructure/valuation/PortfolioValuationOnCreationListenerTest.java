package com.myfinaimanager.core.portfolio.infrastructure.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.myfinaimanager.core.portfolio.business.ValuePortfolioUseCase;
import com.myfinaimanager.core.portfolio.domain.events.PortfolioCreatedEvent;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioValuationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioValuationOnCreationListenerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);
    private static final PortfolioId ID = PortfolioId.newId();

    @Mock private ValuePortfolioUseCase valuePortfolio;
    @Mock private PortfolioValuationRepository valuations;

    private PortfolioValuationOnCreationListener listener() {
        return new PortfolioValuationOnCreationListener(valuePortfolio, valuations, CLOCK);
    }

    @Test
    void delegates_to_the_use_case_on_the_happy_path() {
        doNothing().when(valuePortfolio).value(ID);

        listener().onPortfolioCreated(new PortfolioCreatedEvent(ID));

        verify(valuePortfolio).value(ID);
        verifyNoInteractions(valuations);
    }

    @Test
    void a_valuation_failure_is_swallowed_and_a_failed_snapshot_is_written() {
        doThrow(new PortfolioNotFoundException(ID)).when(valuePortfolio).value(ID);

        listener().onPortfolioCreated(new PortfolioCreatedEvent(ID)); // must not throw

        ArgumentCaptor<PortfolioValuation> snapshot = ArgumentCaptor.forClass(PortfolioValuation.class);
        verify(valuations).upsertLatest(snapshot.capture());
        assertThat(snapshot.getValue().status()).isEqualTo(ValuationStatus.FAILED);
        assertThat(snapshot.getValue().portfolioId()).isEqualTo(ID);
        assertThat(snapshot.getValue().totalValueEUR()).isEmpty();
    }

    @Test
    void a_failure_to_even_write_the_failed_snapshot_is_also_swallowed() {
        doThrow(new IllegalStateException("valuation boom")).when(valuePortfolio).value(any());
        doThrow(new RuntimeException("db boom")).when(valuations).upsertLatest(any());

        listener().onPortfolioCreated(new PortfolioCreatedEvent(ID)); // must not throw

        verify(valuations).upsertLatest(any());
    }
}
