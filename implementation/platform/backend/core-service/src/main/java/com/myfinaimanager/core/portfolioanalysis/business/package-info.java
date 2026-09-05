/**
 * FD005 business orchestration: request creation (automatic on Portfolio creation, manual
 * on-demand), the asynchronous worker that drives PENDING -> RUNNING -> {COMPLETED|FAILED}, and the
 * latest-analysis query. Depends only on {@code portfolioanalysis.domain}; never on
 * {@code portfolioanalysis.infrastructure} (ADR-003; ArchUnit-enforced).
 */
package com.myfinaimanager.core.portfolioanalysis.business;
