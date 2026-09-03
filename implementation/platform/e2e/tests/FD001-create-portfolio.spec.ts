import { expect, test } from '@playwright/test';
import { addPositionFromCatalog } from '../support/add-position';
import { syntheticPosition, uniquePortfolioName } from '../support/data';

/**
 * FD001 — Create Investment Portfolio · E2E-001 (mandatory closure gate).
 *
 * Drives the critical user journey through the REAL running stack:
 *
 *   browser (Chromium) → frontend (nginx + Angular) → REST /api → core-service → PostgreSQL
 *
 * No mocking of frontend↔backend or persistence. Starts from the Create Portfolio screen, not
 * from the API (FR-021 / EN002 §13). Success is asserted from what the Investor sees — the
 * "created successfully" confirmation (FD001 §13 E2E-001, spec FR-036 / SC-013). Persistence
 * guarantees are already covered by the backend integration tests, so no DB assertion is needed
 * here (spec A13).
 *
 * Test data is synthetic and unique per run (support/data.ts); each run also gets a disposable
 * database from `e2e.sh` (EN002 §15/§16).
 */
test('E2E-001: an Investor creates a portfolio with one position and sees the confirmation', async ({
  page,
}) => {
  const pageErrors: string[] = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));

  const portfolioName = uniquePortfolioName();
  const position = syntheticPosition(); // ASML / XAMS / 1 / EUR

  // 1. Open the Create Portfolio screen.
  await page.goto('/portfolios/new');
  await expect(page.getByRole('heading', { name: 'Create portfolio' })).toBeVisible();

  const saveButton = page.getByRole('button', { name: 'Save' });
  await expect(saveButton).toBeDisabled(); // nothing entered yet

  // 2. Enter a portfolio name.
  await page.locator('input[formControlName="name"]').fill(portfolioName);

  // 3. Add one valid position by selecting a catalogued instrument (FD002 changed the input
  //    mechanism; the FD001 outcome — a persisted portfolio + confirmation — is unchanged).
  await addPositionFromCatalog(page, {
    search: position['ticker'],
    optionText: `${position['ticker']} · ${position['market']} · ${position['currency']}`,
    quantity: position['quantity'],
  });

  // 4. The draft list shows the position; Save is now enabled.
  const draftRow = page.locator('app-position-draft-list tbody tr');
  await expect(draftRow).toHaveCount(1);
  await expect(draftRow.first()).toContainText(position['ticker']);
  await expect(saveButton).toBeEnabled();

  // 5. Save.
  await saveButton.click();

  // 6. The Investor sees the success confirmation (FD001 AC-001 / §4.13).
  await expect(page.locator('.notice--success')).toHaveText('Portfolio created successfully.');

  // No uncaught runtime error occurred during the journey.
  expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
});
