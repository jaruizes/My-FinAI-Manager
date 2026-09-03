/**
 * Frontend view models for the Create Portfolio flow (FD001).
 *
 * These mirror the OpenAPI contract shape (`implementation/platform/contracts/openapi/openapi.yaml`),
 * NOT the backend domain. Amounts are kept as strings end to end to avoid binary-float drift
 * (data-model.md §4).
 */

/** One position the investor is drafting, before Save. */
export interface PositionDraft {
  ticker: string;
  market: string;
  /** decimal string, e.g. "12" or "3.5" */
  quantity: string;
  /** ISO 4217 code, e.g. "EUR" */
  currency: string;
  /** ISO date "YYYY-MM-DD"; omitted when the investor did not provide it */
  initialPurchaseDate?: string;
  /** decimal string in `currency`; omitted when the investor did not provide it (never "0") */
  averagePurchasePrice?: string;
}

/** The whole portfolio being created. */
export interface PortfolioDraft {
  name: string;
  positions: PositionDraft[];
}

/** A single validation error returned by the API (`400` ValidationProblem `errors[]`). */
export interface FieldError {
  /** path like `name`, `positions`, `positions[1].quantity` */
  field: string;
  /** canonical code: REQUIRED | AT_LEAST_ONE | INVALID_NUMBER | NOT_POSITIVE |
   *  DUPLICATE_INSTRUMENT | FUTURE_DATE | CURRENCY_FORMAT | NAME_TOO_LONG */
  code: string;
  message: string;
}

/** A position as returned by the API in the created Portfolio. */
export interface PositionView {
  id: string;
  ticker: string;
  market: string;
  quantity: string;
  currency: string;
  initialPurchaseDate: string | null;
  averagePurchasePrice: string | null;
}

/** The created portfolio (contract `Portfolio` schema). */
export interface PortfolioView {
  id: string;
  name: string;
  status: 'ACTIVE';
  positions: PositionView[];
  createdAt: string;
}

/** Result of a create attempt, as the page needs to react to it. */
export type CreatePortfolioOutcome =
  | { kind: 'created'; portfolio: PortfolioView; replayed: boolean }
  | { kind: 'invalid'; errors: FieldError[] }
  | { kind: 'not-saved' }; // transient persistence failure (503) — keep the draft, offer retry
