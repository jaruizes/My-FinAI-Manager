/**
 * Display formatting for FD004 valuation figures. The API sends full-precision decimal strings;
 * the UI shows **2 decimal places** for money and percentages (FD004 §30.3–§30.4 / FR-031). A
 * missing value is `'—'` — never `0` (FR-019).
 */

const MISSING = '—';

/** Format a decimal string as money to 2 dp, e.g. `money('2100', 'EUR')` → `'€2,100.00'`. */
export function money(amount: string | null | undefined, currency: 'EUR' | 'USD'): string {
  if (amount === null || amount === undefined || amount === '') {
    return MISSING;
  }
  const n = Number(amount);
  if (!Number.isFinite(n)) {
    return MISSING;
  }
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(n);
}

/** Format a fraction string (e.g. `'0.761904761905'`) as a percentage to 2 dp → `'76.19%'`. */
export function percent(fraction: string | null | undefined): string {
  if (fraction === null || fraction === undefined || fraction === '') {
    return MISSING;
  }
  const n = Number(fraction);
  if (!Number.isFinite(n)) {
    return MISSING;
  }
  return `${(n * 100).toFixed(2)}%`;
}

/** Format a plain decimal string to 2 dp (for the market-price column), or `'—'`. */
export function decimal2(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return MISSING;
  }
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(2) : MISSING;
}
