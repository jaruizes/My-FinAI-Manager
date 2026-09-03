/**
 * Frontend view models for FD002 controlled instrument selection.
 *
 * `CatalogListing` mirrors the `FinancialInstrument` schema of `GET /api/financial-instruments`
 * (EN004) — business fields only, no provider/persistence data. Amounts/identifiers are strings,
 * as everywhere in the Create Portfolio flow.
 */

/** One selectable catalogued listing returned by the instrument search. */
export interface CatalogListing {
  /** Stable catalog id (uuid) — the option key in the results listbox. */
  id: string;
  name: string;
  /** Normalized trading ticker (uppercase, no provider suffix). */
  ticker: string;
  /** ISO 10383 Market Identifier Code. */
  market: string;
  currency: 'EUR' | 'USD';
  /** Always true in search results (EN004 only returns selectable listings). */
  active: boolean;
  /** ISO 6166 ISIN when the catalog has one; `null`/absent otherwise. */
  isin?: string | null;
}

/** The state of the Add Position instrument search box. */
export type InstrumentSearchState =
  | { kind: 'idle' }
  | { kind: 'searching' }
  | { kind: 'results'; listings: CatalogListing[] }
  | { kind: 'no-results'; query: string }
  | { kind: 'error' };
