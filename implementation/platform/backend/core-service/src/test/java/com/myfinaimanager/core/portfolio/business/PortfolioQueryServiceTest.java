package com.myfinaimanager.core.portfolio.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.InstrumentRef;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Market;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioName;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioStatus;
import com.myfinaimanager.core.portfolio.domain.model.Position;
import com.myfinaimanager.core.portfolio.domain.model.PositionId;
import com.myfinaimanager.core.portfolio.domain.model.Quantity;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;
import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioQueryServiceTest {

    private static final InvestorId DEFAULT_INVESTOR =
            InvestorId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Mock private PortfolioRepository repository;
    @Mock private DefaultInvestorProvider defaultInvestorProvider;

    private PortfolioQueryService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioQueryService(repository, defaultInvestorProvider);
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
    }

    private static Portfolio portfolio(String name) {
        Position position = Position.reconstitute(PositionId.of(UUID.randomUUID()),
                new InstrumentRef(new Ticker("ASML"), new Market("XAMS")),
                new Quantity(new BigDecimal("1")), new Currency("EUR"),
                Optional.empty(), Optional.empty());
        return Portfolio.reconstitute(PortfolioId.of(UUID.randomUUID()), DEFAULT_INVESTOR,
                new PortfolioName(name), PortfolioStatus.ACTIVE, List.of(position), Instant.now());
    }

    @Test
    void list_returns_the_repository_result_for_the_default_investor_in_order() {
        Portfolio a = portfolio("A");
        Portfolio b = portfolio("B");
        when(repository.findAllByInvestor(DEFAULT_INVESTOR)).thenReturn(List.of(b, a));

        assertThat(service.list()).containsExactly(b, a);
        verify(repository, never()).save(any(), anyString());
    }

    @Test
    void view_returns_the_portfolio_when_it_belongs_to_the_investor() {
        Portfolio p = portfolio("Mine");
        when(repository.findByIdForInvestor(p.id(), DEFAULT_INVESTOR)).thenReturn(Optional.of(p));

        assertThat(service.view(p.id())).isSameAs(p);
        verify(repository, never()).save(any(), anyString());
    }

    @Test
    void view_throws_PortfolioNotFoundException_carrying_the_id_when_the_repository_is_empty() {
        PortfolioId id = PortfolioId.of(UUID.randomUUID());
        when(repository.findByIdForInvestor(id, DEFAULT_INVESTOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(id))
                .isInstanceOf(PortfolioNotFoundException.class)
                .satisfies(ex -> assertThat(((PortfolioNotFoundException) ex).portfolioId())
                        .isEqualTo(id.toString()));
        verify(repository, never()).save(any(), anyString());
    }
}
