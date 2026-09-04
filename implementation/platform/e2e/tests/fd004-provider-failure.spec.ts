import { expect, test } from '@playwright/test';
import { createPortfolio, CreatedPortfolio } from '../support/portfolios';
import { uniquePortfolioName } from '../support/data';
import { detail, expectNoFabricatedZeros, valuationState } from '../support/valuation';

/**
 * FD004 — Portfolio Valuation & Allocation · E2E-002 (mandatory closure gate, FD004 §27).
 *
 * The Finnhub stub has no data for MSFT (its `/quote` answer is `{}`, Finnhub's "unknown symbol"
 * response), so EN005's real adapter surfaces "unavailable" and the deterministic valuation records
 * the position as UNVALUED — never `0`. The portfolio must still be created, persisted and visible,
 * and the detail must show an explicit valuation-state message with no fabricated numbers.
 */
test.describe('E2E-002: a provider failure never loses the portfolio or fabricates values', () => {
  const name = `${uniquePortfolioName('FD004')} ProviderFailure`;
  let portfolio: CreatedPortfolio;

  test.beforeAll(async ({ playwright }) => {
    const request = await playwright.request.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://frontend',
    });
    portfolio = await createPortfolio(request, name, [
      { ticker: 'MSFT', market: 'XNAS', quantity: '7', currency: 'USD' },
    ]);
    await request.dispose();
  });

  test('the portfolio is persisted and the detail shows an explicit state, no zeros', async ({
    page,
  }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.goto('/');
    const list = page.locator('app-portfolio-list');
    await expect(list.locator('tbody tr', { hasText: name })).toHaveCount(1);

    await list.getByRole('link', { name }).click();
    await expect(page).toHaveURL(new RegExp(`/portfolios/${portfolio.id}$`));
    await expect(detail(page).locator('.page__title')).toHaveText(name);

    // An explicit valuation state — not a silent success, not a crash.
    await expect(valuationState(page)).toContainText(
      /Valuation unavailable|Partial valuation|Valuation pending/,
    );
    await expectNoFabricatedZeros(page);

    // The position itself is still listed with its entered values.
    await expect(detail(page).locator('tbody tr', { hasText: 'MSFT' })).toContainText('7');

    // Still in the Home list after a reload.
    await page.goto('/');
    await expect(list.locator('tbody tr', { hasText: name })).toHaveCount(1);

    expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
  });
});
