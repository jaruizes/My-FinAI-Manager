/**
 * AI Portfolio Analysis domain (FD005): the {@code PortfolioAnalysis} aggregate and its lifecycle,
 * outbound ports to the two modules this feature reads (`portfolio` for Portfolio/valuation data,
 * `ai` for EN006's provider-neutral AI capability), and failure types. Framework-free — no Spring,
 * HTTP, JSON, JPA, or OpenAI type (ADR-003; ArchUnit-enforced).
 */
package com.myfinaimanager.core.portfolioanalysis.domain;
