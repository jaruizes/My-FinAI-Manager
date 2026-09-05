import { expect, test } from '@playwright/test';
import { createPortfolio, CreatedPortfolio } from '../support/portfolios';
import { uniquePortfolioName } from '../support/data';
import {
  detail,
  expectNoLeakedProviderDetails,
  rerunButton,
  waitForAnalysisTerminal,
} from '../support/portfolioAnalysis';

/**
 * FD005 — AI Portfolio Analysis · E2E-003 (mandatory closure gate, FD005 §45).
 *
 * The controlled AI boundary (`openai-stub`) is configured to fail via the magic marker
 * `E2E_PROVIDER_FAILURE` baked into the Portfolio's name (it flows into the prompt context via
 * `PortfolioAnalysisContextBuilder`, mirroring `finnhub-stub`'s own magic-symbol convention — no
 * stub restart, no mode switch). Flow (FD005 §44): create the Portfolio → Portfolio remains valid
 * → latest analysis reaches FAILED → detail shows the controlled failure state → re-analysis
 * remains possible → no provider error payload or sensitive data is ever displayed.
 */
test.describe('E2E-003: an AI provider failure never invalidates the Portfolio and never leaks internals', () => {
  const name = `${uniquePortfolioName('FD005')} E2E_PROVIDER_FAILURE`;
  let portfolio: CreatedPortfolio;

  test.beforeAll(async ({ playwright }) => {
    const request = await playwright.request.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://frontend',
    });
    portfolio = await createPortfolio(request, name, [
      { ticker: 'AAPL', market: 'XNAS', quantity: '10', currency: 'USD' },
    ]);
    await request.dispose();
  });

  test('the Portfolio stays valid, the analysis reaches FAILED, and re-analysis remains possible', async ({
    page,
  }) => {
    test.setTimeout(90_000);
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.goto('/');
    const list = page.locator('app-portfolio-list');
    // The Portfolio itself was created successfully regardless of the analysis outcome (FR-002).
    await expect(list.locator('tbody tr', { hasText: name })).toHaveCount(1);

    await list.getByRole('link', { name }).click();
    await expect(page).toHaveURL(new RegExp(`/portfolios/${portfolio.id}$`));
    // The position data is intact — the analysis failure touched nothing in FD001/FD004.
    await expect(detail(page)).toContainText('AAPL');

    await waitForAnalysisTerminal(page);

    await expect(detail(page)).toContainText(
      'We could not complete this analysis. You can run it again.',
    );
    await expect(rerunButton(page)).toBeVisible();
    await expect(rerunButton(page)).toBeEnabled();

    await expectNoLeakedProviderDetails(page);

    expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
  });
});
