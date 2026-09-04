/**
 * Domain events published by the {@code portfolio} module (FD004). A domain event is a plain
 * value object — no framework annotation, no transport. {@code PortfolioCreatedEvent} is published
 * in-process (Spring {@code ApplicationEventPublisher}) by {@code CreatePortfolioService} after a
 * genuine creation commits, and consumed synchronously by
 * {@code portfolio.infrastructure.valuation.PortfolioValuationOnCreationListener} to start the
 * FD004 valuation. No message broker, no scheduler (FR-003).
 */
package com.myfinaimanager.core.portfolio.domain.events;
