import { expect, test } from '@playwright/test';
import { createPortfolio, CreatedPortfolio } from '../support/portfolios';
import { uniquePortfolioName } from '../support/data';

/**
 * FD003 — List and View Portfolio Details · E2E-001 (mandatory closure gate, FD003 §16).
 *
 * Drives the real containerized stack (browser → nginx + Angular → REST /api → core-service →
 * PostgreSQL). Setup creates three portfolios via `POST /api/portfolios` (a real production API —
 * allowed by FD003 §16) with 1 / 2 / 3 positions and unique run-scoped names, using catalogued
 * instruments. Then: open Home and assert each portfolio by name + exact position count (other rows
 * tolerated), open the 3-position portfolio, and assert its detail shows its name and exactly its
 * three positions with the created values — none from the other two.
 */
test.describe('E2E-001: list portfolios and view one in detail', () => {
  const run = uniquePortfolioName('FD003');
  const names = { alpha: `${run} Alpha`, beta: `${run} Beta`, gamma: `${run} Gamma` };
  let gamma: CreatedPortfolio;

  test.beforeAll(async ({ playwright }) => {
    const request = await playwright.request.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://frontend',
    });

    await createPortfolio(request, names.alpha, [
      { ticker: 'AAPL', market: 'XNAS', quantity: '11', currency: 'USD' },
    ]);
    await createPortfolio(request, names.beta, [
      { ticker: 'AAPL', market: 'XNAS', quantity: '21', currency: 'USD' },
      { ticker: 'MSFT', market: 'XNAS', quantity: '22', currency: 'USD' },
    ]);
    gamma = await createPortfolio(request, names.gamma, [
      { ticker: 'AAPL', market: 'XNAS', quantity: '31', currency: 'USD' },
      { ticker: 'MSFT', market: 'XNAS', quantity: '32', currency: 'USD' },
      { ticker: 'ASML', market: 'XAMS', quantity: '33', currency: 'EUR' },
    ]);

    await request.dispose();
  });

  test('Home lists all three with exact counts; the detail shows exactly that portfolio', async ({
    page,
  }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.goto('/');
    const list = page.locator('app-portfolio-list');

    for (const [name, count] of [
      [names.alpha, '1'],
      [names.beta, '2'],
      [names.gamma, '3'],
    ] as const) {
      const row = list.locator('tbody tr', { hasText: name });
      await expect(row).toHaveCount(1);
      await expect(row.locator('td').last()).toHaveText(count);
    }

    // Open the 3-position portfolio.
    await list.getByRole('link', { name: names.gamma }).click();
    await expect(page).toHaveURL(new RegExp(`/portfolios/${gamma.id}$`));

    const detail = page.locator('app-portfolio-detail-page');
    await expect(detail.locator('.page__title')).toHaveText(names.gamma);

    const rows = detail.locator('tbody tr');
    await expect(rows).toHaveCount(3);
    const table = await detail.locator('tbody').innerText();
    for (const pos of gamma.positions) {
      expect(table).toContain(pos.ticker);
      expect(table).toContain(pos.quantity);
    }
    // None of Alpha's (q 11) or Beta's (q 21 / 22) positions leak in.
    for (const foreign of ['11', '21', '22']) {
      expect(table.split(/\s+/)).not.toContain(foreign);
    }

    expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
  });
});
