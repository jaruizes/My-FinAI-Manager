package com.myfinaimanager.core.portfolio.infrastructure.persistence.mapper;

import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.model.InstrumentRef;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Market;
import com.myfinaimanager.core.portfolio.domain.model.Money;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioName;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioStatus;
import com.myfinaimanager.core.portfolio.domain.model.Position;
import com.myfinaimanager.core.portfolio.domain.model.PositionId;
import com.myfinaimanager.core.portfolio.domain.model.Quantity;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.PortfolioEntity;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.PositionEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Explicit domain &lt;-&gt; JPA-entity mapping for the {@code Portfolio} aggregate. The domain types
 * stay persistence-agnostic (ADR-003, constitution VI); this is the only place that knows both
 * sides. Reconstruction rebuilds the value objects exactly as the previous JDBC
 * adapter did — same trimming, same "price currency == position currency", same {@code null} ⇔
 * {@link Optional#empty()} handling.
 */
@Component
public class PortfolioPersistenceMapper {

    /** Domain aggregate → a new entity graph ready to {@code saveAndFlush}. */
    public PortfolioEntity toEntity(Portfolio portfolio, String idempotencyKey) {
        PortfolioEntity entity = new PortfolioEntity(
                portfolio.id().value(),
                portfolio.investorId().value(),
                portfolio.name().value(),
                portfolio.status().name(),
                idempotencyKey,
                portfolio.createdAt());

        for (Position position : portfolio.positions()) {
            Money price = position.averagePurchasePrice().orElse(null);
            entity.addPosition(new PositionEntity(
                    position.id().value(),
                    position.instrument().ticker().value(),
                    position.instrument().market().value(),
                    position.quantity().value(),
                    position.currency().code(),
                    position.initialPurchaseDate().orElse(null),
                    price == null ? null : price.amount(),
                    price == null ? null : position.currency().code()));
        }
        return entity;
    }

    /** Entity graph loaded from PostgreSQL → the domain aggregate. */
    public Portfolio toDomain(PortfolioEntity entity) {
        List<Position> positions = new ArrayList<>();
        for (PositionEntity pe : entity.getPositions()) {
            Currency currency = new Currency(pe.getCurrency().trim());
            BigDecimal priceAmount = pe.getAveragePurchasePrice();
            Optional<Money> price = priceAmount == null
                    ? Optional.empty()
                    : Optional.of(new Money(priceAmount, currency));
            positions.add(Position.reconstitute(
                    PositionId.of(pe.getId()),
                    new InstrumentRef(new Ticker(pe.getTicker()), new Market(pe.getMarket())),
                    new Quantity(pe.getQuantity()),
                    currency,
                    Optional.ofNullable(pe.getInitialPurchaseDate()),
                    price));
        }
        return Portfolio.reconstitute(
                PortfolioId.of(entity.getId()),
                InvestorId.of(entity.getInvestorId()),
                new PortfolioName(entity.getName()),
                PortfolioStatus.valueOf(entity.getStatus()),
                positions,
                entity.getCreatedAt());
    }
}
