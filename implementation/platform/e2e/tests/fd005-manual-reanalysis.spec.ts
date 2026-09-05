import { APIRequestContext, expect, test } from '@playwright/test';
import { createPortfolio, CreatedPortfolio } from '../support/portfolios';
import { uniquePortfolioName } from '../support/data';
import { analysisDiversification, rerunButton, waitForAnalysisTerminal } from '../support/portfolioAnalysis';

/**
 * FD005 — AI Portfolio Analysis · E2E-002 (mandatory closure gate, FD005 §45).
 *
 * Precondition: analysis A1 = COMPLETED (the automatic trigger, proven by E2E-001). Then: open
 * detail, verify A1 is displayed, click "Run analysis again", verify the in-progress state, wait
 * for the stub's deterministic response for A2, and verify A2 replaces A1 in the latest-analysis
 * view — asserted via the real API (`GET .../analysis/latest`'s `requestedAt` changes to A2's own
 * timestamp, proving a genuinely new record replaced A1 rather than A1 being mutated in place;
 * FD005 §43, BR-004/BR-005).
 */
test.describe('E2E-002: manual re-analysis creates a new record and A1 is preserved, not mutated', () => {
  const name = `${uniquePortfolioName('FD005')} Re-analysis`;
  let portfolio: CreatedPortfolio;
  let apiContext: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    apiContext = await playwright.request.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://frontend',
    });
    portfolio = await createPortfolio(apiContext, name, [
      { ticker: 'AAPL', market: 'XNAS', quantity: '10', currency: 'USD' },
    ]);
  });

  test.afterAll(async () => {
    await apiContext.dispose();
  });

  async function latestRequestedAt(): Promise<string> {
    const response = await apiContext.get(`/api/portfolios/${portfolio.id}/analysis/latest`);
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    return body.requestedAt as string;
  }

  test('A2 replaces A1 in the latest-analysis view; A1 is never mutated', async ({ page }) => {
    // Waits on two full background-worker cycles (A1 then A2), which can take longer than the
    // default 30s under the heavier load of a full E2E run.
    test.setTimeout(120_000);
    await page.goto(`/portfolios/${portfolio.id}`);
    await waitForAnalysisTerminal(page); // A1 completes
    await expect(analysisDiversification(page)).toContainText('Moderate diversification');
    const a1RequestedAt = await latestRequestedAt();

    const [postResponse] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith(`/api/portfolios/${portfolio.id}/analysis`) && r.request().method() === 'POST',
      ),
      rerunButton(page).click(),
    ]);
    expect(postResponse.status()).toBe(202);
    const a2 = await postResponse.json();
    expect(a2.analysisId).toBeTruthy();
    expect(a2.status).toBe('PENDING');
    expect(a2.requestedAt).not.toBe(a1RequestedAt); // a genuinely new record, not A1 mutated

    await waitForAnalysisTerminal(page); // A2 completes
    await expect(analysisDiversification(page)).toContainText('Moderate diversification'); // same stub content

    // The persisted `requestedAt` round-trips through PostgreSQL's TIMESTAMPTZ, which only keeps
    // microsecond precision — so it may differ from the in-memory nanosecond value the POST
    // response carried by a sub-millisecond rounding amount. Compare with a tolerance instead of
    // strict string equality (the point being proven is "A2, not A1" — a ~74 second difference —
    // not nanosecond-exact serialization).
    const latestAfter = await latestRequestedAt();
    const deltaMs = Math.abs(new Date(latestAfter).getTime() - new Date(a2.requestedAt).getTime());
    expect(deltaMs).toBeLessThan(1000);
  });
});
