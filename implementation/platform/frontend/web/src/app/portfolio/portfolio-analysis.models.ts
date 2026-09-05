/**
 * Frontend view models for FD005 — AI Portfolio Analysis.
 *
 * These mirror the OpenAPI contract shapes (`implementation/platform/contracts/openapi/openapi.yaml`
 * — `PortfolioAnalysis` / `RequestedPortfolioAnalysis` schemas), NOT the backend domain. No
 * provider/model/prompt/token/cost field ever appears here — the API never exposes it (FD005 §28).
 */

export type AnalysisStatus = 'NONE' | 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface OverallDiversificationView {
  level: 'LOW' | 'MODERATE' | 'HIGH';
  explanation: string;
}

export interface KeyInsightView {
  type: string;
  message: string;
}

export interface RiskView {
  type: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  title: string;
  explanation: string;
}

/**
 * The latest AI Portfolio Analysis for a portfolio (contract `PortfolioAnalysis` schema).
 * Content fields are `null` for every status except `COMPLETED`.
 */
export interface PortfolioAnalysisView {
  status: AnalysisStatus;
  requestedAt: string | null;
  completedAt: string | null;
  overallDiversification: OverallDiversificationView | null;
  keyInsights: KeyInsightView[] | null;
  risks: RiskView[] | null;
}

/** Response body for `POST /api/portfolios/{id}/analysis` (contract `RequestedPortfolioAnalysis` schema). */
export interface RequestedPortfolioAnalysisView {
  analysisId: string;
  status: 'PENDING';
  requestedAt: string;
}
