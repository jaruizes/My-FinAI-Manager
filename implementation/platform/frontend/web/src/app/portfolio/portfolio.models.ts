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
