import { expect, test } from '@playwright/test';
import { createPortfolio, CreatedPortfolio } from '../support/portfolios';
import { uniquePortfolioName } from '../support/data';
import {
  analysisDiversification,
  analysisRisk,
  detail,
  rerunButton,
  waitForAnalysisTerminal,
} from '../support/portfolioAnalysis';

/**
 * FD005 — AI Portfolio Analysis · E2E-001 (mandatory closure gate, FD005 §45).
 *
 * Drives the real containerized stack (browser → nginx + Angular → /api → core-service →
 * PostgreSQL) with the AI boundary served by the `openai-stub` container — the REAL
 * `OpenAiModelAdapter` / `OpenAiChatMapper` / async worker chain runs; no live api.openai.com.
 *
 * Flow (FD005 §42): create Portfolio → creation response returns immediately → open Portfolio
 * detail → the analysis starts PENDING/RUNNING → it resolves to the stub's deterministic
 * COMPLETED content.
 */
test.describe('E2E-001: an automatic analysis is requested after Portfolio creation and completes', () => {
  const name = `${uniquePortfolioName('FD005')} Analysis`;
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

  test('the detail page shows the analysis in progress, then the completed content', async ({ page }) => {
    // Waits on a real background worker, which can take longer than the default 30s under the
    // heavier load of a full E2E run (many earlier tests' portfolios also trigger an automatic
    // analysis, sharing the same small executor pool).
    test.setTimeout(90_000);
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.goto('/');
    const list = page.locator('app-portfolio-list');
    await expect(list.locator('tbody tr', { hasText: name })).toHaveCount(1);
    await list.getByRole('link', { name }).click();
    await expect(page).toHaveURL(new RegExp(`/portfolios/${portfolio.id}$`));

    // The analysis is initially open (PENDING/RUNNING) or may already have completed if the
    // background worker was fast — either is acceptable; only the eventual terminal state matters.
    await waitForAnalysisTerminal(page);

    await expect(analysisDiversification(page)).toContainText('Moderate diversification');
    await expect(analysisDiversification(page)).toContainText('Concentrated in Technology.');
    await expect(detail(page)).toContainText('Technology represents the majority of the Portfolio.');
    await expect(analysisRisk(page, 'Sector concentration')).toContainText(
      'The Portfolio is concentrated in a single sector.',
    );
    await expect(detail(page)).toContainText('Analysed');
    await expect(rerunButton(page)).toBeVisible();

    expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
  });
});
