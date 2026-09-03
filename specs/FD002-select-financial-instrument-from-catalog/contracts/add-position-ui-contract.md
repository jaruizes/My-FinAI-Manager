# UI contract — the Add Position interaction (FD002)

The observable contract of the reworked `add-position.dialog` component. Governs the frontend unit
tests and the E2E. Visual styling defers to `product/ux/design-system.md`; this file fixes
**behavior, structure, and accessibility**.

---

## U1 — No free-text ticker / market / currency (FR-001)

The dialog MUST NOT contain a text input bound to `ticker`, `market`, or `currency`. Those three
values are set **only** by selecting a catalogued instrument (+ listing). A test asserts there is no
`input[formControlName="ticker"|"market"|"currency"]`.

## U2 — Search combobox (FR-002, FR-004, FR-024)

- A search `<input role="combobox">` with `aria-expanded`, `aria-controls` → the results
  `<ul role="listbox" id=…>`, and `aria-activedescendant` → the focused `<li role="option" id=…>`.
- Each option row shows, as text: **`«name» — «TICKER» · «MIC» · «CCY»`**, and appends
  **`· ISIN «isin»`** when `isin` is present.
- Typing drives search per research D5: trim, debounce ~250 ms, `distinctUntilChanged`, min length
  **1**, `switchMap` (cancel in-flight). A blank/whitespace box issues **no** request (state → `idle`).
- Keyboard: `ArrowDown` / `ArrowUp` move the active option (wrapping optional), `Enter` selects the
  active option, `Escape` closes the listbox (and, if nothing selected yet, returns to `idle`).
  Mouse click on a row selects it. Visible focus indicator throughout.

## U3 — States (FR-013, FR-014, FR-015, FR-025)

| State | Trigger | The dialog shows | "Add position" button |
|---|---|---|---|
| `idle` | empty/blank search box | hint text ("Search by ticker or company name") | disabled |
| `searching` | request in flight | localized spinner; rest of the dialog still usable | disabled |
| `results` | ≥ 0 listings returned for a non-blank query | the listbox | disabled until a selection is made |
| `no-results` | 0 listings for a non-blank query | "No matching instrument found." — **no** control to accept the typed text | disabled |
| `selected` | an instrument (+ listing) chosen | name + `TICKER · MIC · CCY`, a "Change" affordance, and — when required — the listing selector (U4) | enabled once quantity is a valid number (FD001 rule) |
| `error` | search HTTP failure | "We couldn't search right now. Please try again." + **Retry** (re-runs the last query) | disabled |

An `error` or `no-results` state MUST NOT let the Investor proceed with the typed text as an
instrument (FR-013).

## U4 — Constrained listing selector (FR-007)

After an instrument is chosen:

- Compute `listings = allResultsForThisInstrument` — every returned `CatalogListing` whose
  **normalized name** (`trim().toLocaleUpperCase()`) equals the chosen one's (research D7).
- If `listings.length >= 2`: render a Market/Currency selector (`<select>` or segmented control)
  whose options are exactly those listings, each labelled `«MIC» · «CCY»`. Choosing one sets
  `ticker/market/currency` from that listing. No option corresponds to a non-catalogued combination.
- If `listings.length <= 1`: apply the single listing's `ticker/market/currency` directly; no
  selector shown.
- The Investor cannot confirm until, where the selector is shown, a listing is chosen.

*(EN004's current catalog has no multi-listing instruments, so `>= 2` is exercised by a synthetic
unit test only.)*

## U5 — Downstream fields unchanged (FR-018)

Quantity (required, numeric — FD001 rule kept), Initial purchase date (optional, `type="date"`),
Average purchase price (optional, numeric). The price label reads **`(optional, in «selected CCY»)`**
and updates when the selected listing's currency changes.

## U6 — Emitting the draft

On confirm, the dialog emits a `PositionDraft` with `ticker` / `market` / `currency` filled from the
selected listing and the FD001 fields as entered. The wire body of `POST /api/portfolios` is
unchanged (research D8).

## U7 — Rendering the server's `INSTRUMENT_NOT_IN_CATALOG` (FR-010, edge: listing went inactive)

If `POST /api/portfolios` returns a `400` with an `errors[]` entry
`{ field: "positions[i]", code: "INSTRUMENT_NOT_IN_CATALOG" }`, `create-portfolio.page` displays it
against that position (same mechanism as every other position field error) with the server's
`message`. No page-logic change beyond widening the `code` union.

## U8 — Accessibility (design-system §Accessibility; FR-024)

Semantic `<label>` for the search field and every subsequent control; listbox fully keyboard
operable; active option conveyed via `aria-activedescendant` (not only visual highlight); error and
no-results text not conveyed by color alone; the dialog keeps `role="dialog"` / `aria-modal` /
labelled title and an obvious Cancel (FD001, unchanged).

---

## Test hooks (for `add-position.dialog.spec.ts` and the E2E)

- `getByRole('combobox')` — the search field.
- `getByRole('listbox')` / `getByRole('option', { name: /Apple Inc\. — AAPL · XNAS · USD/ })`.
- `getByRole('button', { name: 'Add position' })` — disabled/enabled per U3.
- No `getByLabel('Ticker'|'Market'|'Currency')` text inputs (U1).
- `getByText('No matching instrument found.')` (U3 `no-results`).
- `getByRole('button', { name: 'Retry' })` (U3 `error`).
