/**
 * FD004 valuation orchestration adapters. {@code PortfolioValuationOnCreationListener} is a
 * synchronous Spring {@code @EventListener} on {@code PortfolioCreatedEvent}: it invokes
 * {@code ValuePortfolioUseCase} after the create transaction has committed, inside a catch-all so a
 * valuation failure can never roll back or hide the created Portfolio (FR-002, FR-004; SC-005,
 * SC-008). No {@code @Async}, no broker, no scheduler.
 */
package com.myfinaimanager.core.portfolio.infrastructure.valuation;
