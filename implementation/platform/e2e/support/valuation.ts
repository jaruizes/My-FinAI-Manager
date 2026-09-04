import { Locator, Page, expect } from '@playwright/test';

/** Helpers for asserting the FD004 valuation on the portfolio detail page. */

export function detail(page: Page): Locator {
  return page.locator('app-portfolio-detail-page');
}

/** The whole valuation-state line ("Valued at …", "Partial valuation — …", "Valuation unavailable"). */
export function valuationState(page: Page): Locator {
  return detail(page).locator('.valuation__state');
}

/** The row in the detail positions table for a given ticker. */
export function positionRow(page: Page, ticker: string): Locator {
  return detail(page).locator('tbody tr', { hasText: ticker });
}

/** The "Allocation by Ticker" pie chart (FD004 §17.1). */
export function tickerChart(page: Page): Locator {
  return detail(page).locator('app-pie-chart', { hasText: 'Allocation by Ticker' });
}

/** The "Allocation by Sector" pie chart (FD004 §17.2). */
export function sectorChart(page: Page): Locator {
  return detail(page).locator('app-pie-chart', { hasText: 'Allocation by Sector' });
}

/** Legend text of a pie chart (label + percentage per slice), empty string if the chart is absent. */
export async function chartLegend(chart: Locator): Promise<string> {
  return (await chart.count()) === 0 ? '' : ((await chart.locator('.chart__legend').innerText()) ?? '');
}

/** Assert no fabricated zero appears anywhere in the detail's valuation surface (FR-019, E2E-002). */
export async function expectNoFabricatedZeros(page: Page): Promise<void> {
  const text = (await detail(page).innerText()) ?? '';
  expect(text).not.toContain('$0.00');
  expect(text).not.toContain('€0.00');
  expect(text).not.toMatch(/(^|\s)0\.00(\s|$)/);
  expect(text).not.toMatch(/(^|\s)0%(\s|$)/);
  expect(text).not.toMatch(/(^|\s)0\.00%/);
}
