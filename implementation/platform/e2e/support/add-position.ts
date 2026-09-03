import { Page, expect } from '@playwright/test';

/**
 * Drive the FD002 Add Position dialog: open it, search the catalog, select an instrument, enter a
 * quantity, and confirm. The three reference values (ticker / market / currency) are controlled by
 * the selected catalogued listing — there are no free-text inputs for them (FD002 FR-001).
 *
 * @param optionText a substring of the wanted result row. The row renders the instrument name and
 *        a `TICKER · MIC · CCY` line, so a value like "AAPL · XNAS · USD" identifies it uniquely.
 */
export async function addPositionFromCatalog(
  page: Page,
  opts: { search: string; optionText: string; quantity: string },
): Promise<void> {
  await page.locator('.positions__header').getByRole('button', { name: 'Add position' }).click();

  const dialog = page.getByRole('dialog', { name: 'Add position' });
  await expect(dialog).toBeVisible();

  // No free-text ticker / market / currency inputs (FD002 FR-001 / SC-001).
  await expect(dialog.locator('input[formControlName="ticker"]')).toHaveCount(0);
  await expect(dialog.locator('input[formControlName="market"]')).toHaveCount(0);
  await expect(dialog.locator('input[formControlName="currency"]')).toHaveCount(0);

  await dialog.getByRole('combobox').fill(opts.search);
  const option = dialog.getByRole('option').filter({ hasText: opts.optionText });
  await expect(option).toBeVisible();
  await option.click();

  await dialog.getByLabel('Quantity').fill(opts.quantity);
  await dialog.getByRole('button', { name: 'Add position' }).click();
  await expect(dialog).toBeHidden();
}
