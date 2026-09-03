package com.myfinaimanager.core.portfolio.domain.ports;

import com.myfinaimanager.core.portfolio.domain.model.InvestorId;

/**
 * Supplies the single platform-seeded default investor that owns every portfolio in FD001
 * (ADR-002; spec A2 / FR-030). Real multi-investor identity is a later feature.
 */
public interface DefaultInvestorProvider {

    InvestorId get();
}
