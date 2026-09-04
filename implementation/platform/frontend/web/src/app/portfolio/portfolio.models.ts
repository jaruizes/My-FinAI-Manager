/**
 * Frontend view models for viewing portfolios (FD003 — List and View Portfolio Details).
 *
 * These mirror the OpenAPI contract shape (`implementation/platform/contracts/openapi/openapi.yaml`),
 * NOT the backend domain. The detail view reuses the FD001 `Portfolio` / `Position` response
 * shapes, re-exported here as {@link PortfolioView} / {@link PositionView}.
 */

import type { PortfolioView } from './portfolio-creation.models';

export type { PortfolioView, PositionView } from './portfolio-creation.models';

/** One row of the Home portfolios list (contract `PortfolioSummary` schema). */
export interface PortfolioSummary {
  id: string;
  name: string;
  positionCount: number;
}

/** Load state of the Home portfolios list. `loaded` with an empty array drives the empty state. */
export type ListState =
  | { kind: 'loading' }
  | { kind: 'loaded'; portfolios: PortfolioSummary[] }
  | { kind: 'error' };

/** Load state of a single portfolio detail view. */
export type DetailState =
  | { kind: 'loading' }
  | { kind: 'loaded'; portfolio: PortfolioView }
  | { kind: 'not-found' }
  | { kind: 'error' };

/* ---- FD004 — Portfolio Valuation & Allocation (contract `PortfolioValuation` schema) ---- */

export type ValuationStatus = 'PENDING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';

/** One position's valuation. Monetary fields are `null` (never "0") when `valued` is false. */
export interface PositionValuationView {
  ticker: string;
  market: string;
  quantity: string;
  nativeCurrency: 'EUR' | 'USD';
  valued: boolean;
  marketPrice: string | null;
  nativeMarketValue: string | null;
  valueInEUR: string | null;
  valueInUSD: string | null;
  /** fraction of the portfolio's total EUR value, e.g. "0.761904761905" */
  portfolioWeight: string | null;
  sector: string;
  priceObservedAt: string | null;
}

/** One sector's share of the portfolio, in the canonical EUR basis. */
export interface SectorAllocationView {
  sector: string;
  sectorValueEUR: string;
  /** fraction, e.g. "0.761904761905" */
  sectorWeight: string;
}

/**
 * One slice of an allocation pie chart (FD004 §17.1/§17.2). `fraction` is the deterministic
 * backend weight (`portfolioWeight` for the ticker chart, `sectorWeight` for the sector chart) —
 * the chart NEVER recomputes it (§22 BR-016).
 */
export interface AllocationSlice {
  label: string;
  /** 0..1 — the backend weight, used verbatim. */
  fraction: number;
}

/** The latest valuation snapshot for a portfolio. */
export interface PortfolioValuationView {
  portfolioId: string;
  status: ValuationStatus;
  calculatedAt: string | null;
  totalValueEUR: string | null;
  totalValueUSD: string | null;
  marketDataAsOf: string | null;
  fxDataAsOf: string | null;
  positions: PositionValuationView[];
  sectors: SectorAllocationView[];
}
