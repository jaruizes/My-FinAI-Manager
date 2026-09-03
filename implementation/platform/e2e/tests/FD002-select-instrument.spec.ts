import { expect, test } from '@playwright/test';
import { addPositionFromCatalog } from '../support/add-position';
import { uniquePortfolioName } from '../support/data';

/**
 * FD002 — Select Financial Instrument from Catalog · mandatory closure journey (FR-028, SC-010).
 *
 * Drives the real containerized stack (browser → nginx + Angular → REST /api → core-service →
 * PostgreSQL). The catalog is populated on backend start from EN004's committed fixtures — no
 * external reference-data provider is contacted at any point (SC-007).
 *
 * FD002 must not be accepted, closed, or marked Completed while this test is missing or failing
 * (FD002 §14, §17.11).
 */
test('FD002: an Investor adds positions by searching and selecting catalogued instruments', async ({
  page,
}) => {
  const pageErrors: string[] = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));

  // No call to any external reference-data provider — every request the page makes stays on the
  // frontend origin (SC-007 / FR-003 / BR-008). The frontend proxies /api to the backend, so the
  // browser only ever talks to one host.
  const httpRequests: string[] = [];
  page.on('request', (req) => {
    if (req.url().startsWith('http')) {
      httpRequests.push(req.url());
    }
  });

  await page.goto('/portfolios/new');
  await expect(page.getByRole('heading', { name: 'Create portfolio' })).toBeVisible();
  await page.locator('input[formControlName="name"]').fill(uniquePortfolioName('FD002'));

  // USD instrument — search by company name, select the listing.
  await addPositionFromCatalog(page, {
    search: 'Apple',
    optionText: 'AAPL · XNAS · USD',
    quantity: '3',
  });

  // EUR instrument — search by ticker (lower case), exercises the EUR path (FR-026).
  await addPositionFromCatalog(page, {
    search: 'san',
    optionText: 'SAN · XMAD · EUR',
    quantity: '5',
  });

  const rows = page.locator('app-position-draft-list tbody tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(0)).toContainText('Apple Inc.');
  await expect(rows.nth(0)).toContainText('AAPL');
  await expect(rows.nth(0)).toContainText('XNAS');
  await expect(rows.nth(1)).toContainText('XMAD');

  const saveButton = page.getByRole('button', { name: 'Save' });
  await expect(saveButton).toBeEnabled();
  await saveButton.click();

  await expect(page.locator('.notice--success')).toHaveText('Portfolio created successfully.');

  const appHost = new URL(page.url()).host;
  const external = httpRequests.filter((u) => new URL(u).host !== appHost);
  expect(external, `requests left the frontend origin: ${external.join(' | ')}`).toHaveLength(0);
  expect(httpRequests.some((u) => u.includes('/api/financial-instruments'))).toBe(true);
  expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
});

test('FD002: a search that matches nothing tells the Investor and does not accept the text', async ({
  page,
}) => {
  await page.goto('/portfolios/new');
  await page.locator('.positions__header').getByRole('button', { name: 'Add position' }).click();
  const dialog = page.getByRole('dialog', { name: 'Add position' });
  await dialog.getByRole('combobox').fill('zzzznosuchinstrument');
  await expect(dialog.getByText('No matching instrument found.')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Add position' })).toBeDisabled();
});
