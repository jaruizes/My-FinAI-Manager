/**
 * Synthetic test-data helpers for E2E specs (EN002, FR-026 / FR-027).
 *
 * Rules:
 *  - Values are collision-free so repeated runs and parallel tests never contaminate each other,
 *    on top of the disposable database `e2e.sh` gives every run.
 *  - NEVER use real personal portfolio information.
 *  - NEVER introduce a test-only backend endpoint for data setup — drive the real UI.
 *
 * The platform smoke test does not need these; they exist for feature specs (e.g. FD001).
 */

function token(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/** A unique, human-readable portfolio name, e.g. `"E2E l1a2b3-x9y8z7"`. */
export function uniquePortfolioName(prefix = 'E2E'): string {
  return `${prefix} ${token()}`;
}

/** A client-generated idempotency key for a create attempt. */
export function uniqueIdempotencyKey(): string {
  return `e2e-${token()}-${token()}`;
}

/** A synthetic position payload (well-formed by construction). */
export function syntheticPosition(overrides: Partial<Record<string, string>> = {}): Record<string, string> {
  return {
    ticker: 'ASML',
    market: 'XAMS',
    quantity: '1',
    currency: 'EUR',
    ...overrides,
  };
}
