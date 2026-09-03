import { APIRequestContext, expect } from '@playwright/test';
import { uniqueIdempotencyKey } from './data';

/** A position payload for {@link createPortfolio}. Uses catalogued instruments (EN004). */
export interface PositionPayload {
  ticker: string;
  market: string;
  quantity: string;
  currency: string;
  initialPurchaseDate?: string;
  averagePurchasePrice?: string;
}

export interface CreatedPortfolio {
  id: string;
  name: string;
  positions: { ticker: string; market: string; quantity: string; currency: string }[];
}

/**
 * Create a portfolio through the real production API (`POST /api/portfolios`) — allowed for FD003
 * E2E setup (FD003 §16). The frontend proxies `/api` to the backend, so the request goes through
 * the same origin the browser uses.
 */
export async function createPortfolio(
  request: APIRequestContext,
  name: string,
  positions: PositionPayload[],
): Promise<CreatedPortfolio> {
  const response = await request.post('/api/portfolios', {
    headers: { 'Idempotency-Key': uniqueIdempotencyKey(), 'Content-Type': 'application/json' },
    data: { name, positions },
  });
  expect(response.ok(), `POST /api/portfolios -> ${response.status()} ${await response.text()}`).toBeTruthy();
  const body = await response.json();
  return {
    id: body.id,
    name: body.name,
    positions: body.positions.map((p: PositionPayload) => ({
      ticker: p.ticker,
      market: p.market,
      quantity: p.quantity,
      currency: p.currency,
    })),
  };
}
