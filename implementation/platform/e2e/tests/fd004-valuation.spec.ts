import { expect, test } from '@playwright/test';
import { createPortfolio, CreatedPortfolio } from '../support/portfolios';
import { uniquePortfolioName } from '../support/data';
import {
  chartLegend,
  detail,
  positionRow,
  sectorChart,
  tickerChart,
  valuationState,
} from '../support/valuation';

/**
 * FD004 — Portfolio Valuation & Allocation · E2E-001 (mandatory closure gate, FD004 §26).
 *
 * Drives the real containerized stack (browser → nginx + Angular → /api → core-service →
 * PostgreSQL) with the Finnhub boundary served by the `finnhub-stub` container — EN005's REAL
 * adapter / mapper / cache / error-translation runs; no live finnhub.io.
 *
 * Deterministic inputs (from the stub): AAPL @ 200 USD / Technology, SAN @ 5 EUR / Financial
 * Services, USD→EUR 0.80, EUR→USD 1.25. Portfolio: AAPL ×10 (USD), SAN ×100 (EUR).
 * Expected: total EUR 2,100.00, total USD 2,625.00; AAPL weight 76.19% (Technology),
 * SAN weight 23.81% (Financial Services).
 */
test.describe('E2E-001: a created portfolio is valued and the detail shows it', () => {
  const name = `${uniquePortfolioName('FD004')} Valuation`;
  let portfolio: CreatedPortfolio;

  test.beforeAll(async ({ playwright }) => {
    const request = await playwright.request.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://frontend',
    });
    portfolio = await createPortfolio(request, name, [
      { ticker: 'AAPL', market: 'XNAS', quantity: '10', currency: 'USD' },
      { ticker: 'SAN', market: 'XMAD', quantity: '100', currency: 'EUR' },
    ]);
    await request.dispose();
  });

  test('totals, per-position valuation, weights and sector allocation are correct', async ({
    page,
  }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    // Creation succeeded and the portfolio is in the Home list.
    await page.goto('/');
    const list = page.locator('app-portfolio-list');
    await expect(list.locator('tbody tr', { hasText: name })).toHaveCount(1);

    await list.getByRole('link', { name }).click();
    await expect(page).toHaveURL(new RegExp(`/portfolios/${portfolio.id}$`));

    // Valuation state + totals.
    await expect(valuationState(page)).toContainText('Valued at');
    const totals = detail(page).locator('.totals');
    await expect(totals).toContainText('€2,100.00');
    await expect(totals).toContainText('$2,625.00');

    // Per-position columns — Market price carries the native currency (FR-029).
    const aapl = positionRow(page, 'AAPL');
    await expect(aapl).toContainText('76.19%');
    await expect(aapl).toContainText('Technology');
    await expect(aapl).toContainText('200.00 USD');
    const san = positionRow(page, 'SAN');
    await expect(san).toContainText('23.81%');
    await expect(san).toContainText('Financial Services');
    await expect(san).toContainText('5.00 EUR');

    // The native "Market value" column and the standalone sector-allocation list are gone —
    // the sector percentages now live in the Allocation by Sector chart legend (FR-028).
    await expect(detail(page).locator('.sectors')).toHaveCount(0);
    await expect(detail(page).locator('thead th', { hasText: /^Market value$/ })).toHaveCount(0);

    // Allocation pie charts (FD004 §17.1/§17.2; §26 checks 11–15). Both mandatory, both
    // consistent with the deterministic weights above.
    await expect(tickerChart(page)).toBeVisible();
    const tickerLegend = await chartLegend(tickerChart(page));
    expect(tickerLegend).toContain('AAPL');
    expect(tickerLegend).toContain('76.19%');
    expect(tickerLegend).toContain('SAN');
    expect(tickerLegend).toContain('23.81%');

    await expect(sectorChart(page)).toBeVisible();
    const sectorLegend = await chartLegend(sectorChart(page));
    expect(sectorLegend).toContain('Technology');
    expect(sectorLegend).toContain('76.19%');
    expect(sectorLegend).toContain('Financial Services');
    expect(sectorLegend).toContain('23.81%');

    // Still persisted after a reload.
    await page.goto('/');
    await expect(list.locator('tbody tr', { hasText: name })).toHaveCount(1);

    expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
  });
});
