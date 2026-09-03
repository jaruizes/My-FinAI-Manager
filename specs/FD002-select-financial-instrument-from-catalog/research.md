# Research — FD002 Select Financial Instrument from Catalog

Phase 0 of `/speckit-plan`. Each decision: **Decision · Rationale · Alternatives considered**.
Inputs: the approved FD002 Feature Definition, EN004 (delivered catalog + `GET /api/financial-instruments`),
FD001 (`POST /api/portfolios`, `Portfolio.create`, `PortfolioValidationException`, `ValidationCode`),
ADR-003, `architecture-rules.md` (AR-005/AR-006), `product/ux/design-system.md`, the constitution.

No `NEEDS CLARIFICATION` markers survive. The two material spec questions were resolved with the
user on 2026-09-03 (constrained selectors; backend enforcement). Three residual items are technical
Open Decisions (OD-FD002-2/4/5) carried into `plan.md` for confirmation.

---

## D1 — Where the catalog-membership rule runs (OD-FD002-1)

**Decision**: In **`CreatePortfolioService`** (the `portfolio` business layer). It calls a new
`portfolio.domain.ports.InstrumentCatalog` port for every position that `Portfolio.create` parsed
successfully, and merges an `INSTRUMENT_NOT_IN_CATALOG` `Violation` (scoped to `positions[i]`) with
the structural violations, raising **one** `PortfolioValidationException` (all problems in one
`400`, exactly as FD001 already does). `Portfolio.create` is unchanged — it keeps only its `Clock`
dependency.

Concretely:

```text
CreatePortfolioService.create(command):
  replay check (unchanged)
  investorId = defaultInvestorProvider.get()
  structural = []
  try: portfolio = Portfolio.create(investorId, name, positions, clock)
  catch PortfolioValidationException e: structural = e.violations()
  catalog = []
  for i, pos in positions:
     if pos.ticker non-blank AND pos.market non-blank AND Currency.hasValidShape(pos.currency):  # else FD001 REQUIRED/CURRENCY_FORMAT owns it
        if not instrumentCatalog.isSelectable(new Ticker(pos.ticker), new Market(pos.market), new Currency(pos.currency)):
           catalog += Violation(positions[i], INSTRUMENT_NOT_IN_CATALOG,
                                "<ticker> on <market> in <currency> is not a selectable instrument.")
  if structural or catalog: throw new PortfolioValidationException(structural ++ catalog)
  save(portfolio) ...   # portfolio is guaranteed non-null here (structural empty)
```

**Rationale**: Reference-data membership is a *different category* of rule from the FD001 structural
/ intra-portfolio rules (required, number format, positivity, future date, duplicate). Keeping it in
the service (which is *allowed* to depend on ports and orchestrate — ADR-003 §Business) leaves
`Portfolio.create` a pure function of raw input + `Clock`, and avoids threading a catalog dependency
through the aggregate factory. All-violations-in-one-pass (FD001 FR-024) is preserved by merging
lists.

**Currency participates in the check (pinned 2026-09-03 — analyze finding A1).** The rule is
"the full `ticker + market + currency` is one active catalogued listing" (FD002 BR-004). Because
the frontend fills currency from the chosen listing (FR-006/FR-007) a mismatch cannot arise from the
UI, but FR-011 requires the guarantee to hold for **any** client — so a raw
`AAPL + XNAS + EUR` (USD listing) is rejected with `INSTRUMENT_NOT_IN_CATALOG`. A position missing a
required `currency` is already caught by the FD001 `REQUIRED` / `CURRENCY_FORMAT` structural check
and is not catalog-checked. The port therefore takes `Currency` (see D2/D3).

**Alternatives considered**:
- *Add `InstrumentCatalog` to `Portfolio.create(...)`* — the aggregate would call the port inside
  `parsePosition`. Rejected: widens the aggregate's dependency surface, couples a pure domain
  factory to a query port, and makes `Portfolio.create` unit tests need a catalog fake for every
  case. The `Clock` precedent is weaker justification than it looks — `Clock` is a JDK type for
  "today", not an outbound query.
- *Validate in the REST controller/adapter* — rejected: business rule in an adapter (CLAUDE.md §7;
  ADR-003).
- *A separate `ValidatePositionsAgainstCatalog` use case invoked by the controller before
  `CreatePortfolio`* — rejected: two use cases for one atomic "create a valid portfolio" operation;
  harder to keep the single-`400`-with-all-errors behavior.

---

## D2 — The ACL port + adapter seam (OD-FD002-1, OD-FD002-4)

**Decision**:
- **`portfolio.domain.ports.InstrumentCatalog`** — `boolean isSelectable(Ticker ticker, Market market, Currency currency)`
  (currency added per A1). Uses `portfolio`'s **own** `Ticker` / `Market` / `Currency` value objects.
  Owned by the `portfolio` domain;
  `portfolio.domain` / `portfolio.business` know nothing about `financialinstrument`.
- **`portfolio.infrastructure.catalog.CatalogInstrumentCatalogAdapter`** (`@Component`) implements
  it by calling `financialinstrument.domain.ports.FinancialInstrumentCatalog.findSelectable(...)`
  (D3) and returning `result.isPresent()`. This adapter is the **only** place the two modules touch.
- **ArchUnit** (D11): `portfolio` must not depend on `..financialinstrument.infrastructure..` — only
  `..financialinstrument.domain.ports..` and `..financialinstrument.domain.model..`.

**Rationale**: AR-006 — "cross-boundary access must occur through an explicit application interface".
`financialinstrument.domain.ports.FinancialInstrumentCatalog` *is* that published interface (its
javadoc already says "the business layer depends only on this interface"). The `portfolio`-side ACL
port keeps the coupling out of `portfolio`'s core and makes the rule trivially unit-testable with a
fake. One deployable (ADR-001) means an in-process method call is the right mechanism — no HTTP, no
event.

**Alternatives considered**:
- *`portfolio.business` depends directly on `financialinstrument.domain.ports`* — works and passes
  today's ArchUnit, but couples `portfolio`'s business layer to another module's model
  (`FinancialInstrumentListing`). The thin ACL port costs ~5 lines and removes that coupling.
- *A shared-kernel / `reference` module exposing a neutral contract* — rejected as premature for one
  boolean method (YAGNI; AR-005 wants explicit boundaries, not maximal indirection).
- *Publish a `PositionInstrumentValidated` event* — rejected: synchronous validation on the request
  path; an event adds latency and eventual-consistency semantics for no benefit (AR-016, AR-019).

---

## D3 — `financialinstrument.findSelectable(ticker, marketMic)` (OD-FD002-5)

**Decision**: Add one method to the existing port
`financialinstrument.domain.ports.FinancialInstrumentCatalog`:

```java
/** The one active EUR/USD listing with this exact ticker (case-insensitive) on this exact MIC,
 *  or empty. Used by FD002 to validate a Position's instrument selection. */
Optional<FinancialInstrumentListing> findSelectable(String ticker, String marketMic);
```

Implemented in `FinancialInstrumentCatalogAdapter` with the **existing**
`FinancialInstrumentJpaRepository.findByTickerIgnoreCaseAndMarketMic(ticker, marketMic)` plus a
guard `listing.active() && SupportedCurrency.isSupported(listing.currency())` (or a dedicated
derived query `findByTickerIgnoreCaseAndMarketMicAndActiveTrueAndCurrencyIn(...)`). Read-only,
`@Transactional(readOnly = true)`. **No schema change, no ingestion change, no new REST endpoint.**

**Lookup key = `ticker + market`; currency is then matched in `portfolio` (pinned 2026-09-03 —
A1).** `findSelectable` stays a `ticker + market` lookup (the DB unique key is `(ticker, market_mic)`,
a listing has exactly one currency, FD001 identity is `ticker + market` — BR-009). The
**currency comparison happens in the `portfolio` adapter**: `CatalogInstrumentCatalogAdapter`
resolves the listing via `findSelectable`, then `isSelectable` returns `true` only if
`listing.currency().name().equalsIgnoreCase(currency.value())`. So a raw `AAPL + XNAS + EUR`
(USD listing) → `INSTRUMENT_NOT_IN_CATALOG`. This keeps `financialinstrument`'s port a pure
reference lookup and puts the "the submitted combination must match" rule where it belongs — in the
consuming module. Fully closes AC-006 (both `AAPL + XMAD + EUR` wrong-market and `AAPL + XNAS + EUR`
wrong-currency).

**Rationale**: An exact membership check is a distinct query from the ranked free-text `search()`.
Reusing `search()` would return many rows and force client-side filtering with wrong ordering
semantics. EN004 is "closed" but FD002 is explicitly a backend feature (FD002 §13) and this only
widens the **consumable** surface of an already-owned port — no behavior of EN004's ingestion or
its endpoint changes. The change is additive and covered by a new `financialinstrument` IT.

**Alternatives considered**:
- *Filter `search(ticker)` results in the adapter* — rejected (semantics/ordering, N rows).
- *Add a second REST endpoint `GET /api/financial-instruments/{ticker}/{mic}`* — rejected: FD002
  needs the check server-side inside portfolio creation, not over HTTP; FR-019 forbids a second
  endpoint.
- *A `MarketCatalog`-style bulk `findAllSelectable(Set<key>)`* — nice for a multi-position portfolio
  (one query instead of N). **Adopt if the position count can be > ~5**; for the current scope N is
  tiny. Recorded as a `/speckit-tasks` optimization, not a blocker.

---

## D4 — New validation code + contract shape (OD-FD002-3)

**Decision**:
- `portfolio.domain.model.ValidationCode` gains **`INSTRUMENT_NOT_IN_CATALOG`**.
- `implementation/platform/contracts/openapi/openapi.yaml` — add `INSTRUMENT_NOT_IN_CATALOG` to
  `ValidationProblem.properties.errors.items.properties.code.enum`, extend the `description`, and add
  an example (a `positions[0]` entry). `type` stays **`/problems/portfolio-validation`**, status
  `400`. OpenAPI dialect stays **3.0.3**.
- The existing `PortfolioExceptionHandler` serializes `violations()` generically — **no handler
  change**. The frontend's `classifyError` already maps `400 → { kind: 'invalid', errors }` — **no
  API-service change**; the page renders the new `field/code/message` like any other.
- New contract test case in `CreatePortfolioControllerContractTest`: a non-catalogued position →
  `400` body conforms to `openapi.yaml` and carries `errors[].code = INSTRUMENT_NOT_IN_CATALOG`.

**Rationale**: The failure is one of several *position validation* failures — it belongs in the same
`ValidationProblem` alongside `NOT_POSITIVE`, `DUPLICATE_INSTRUMENT`, etc. Additive enum entry =
non-breaking for existing clients (FD001 spec's compatibility expectation; constitution VIII "no
breaking changes without approval"). Contract-first: the OpenAPI edit + contract test land before
the service emits the code.

**Alternatives considered**:
- *A dedicated problem `type`* — rejected (it is not a distinct problem class; it is a broken
  business rule on a position).
- *HTTP `422`* — rejected: FD001 uses `400` for all `ValidationProblem`s; stay consistent.

---

## D5 — Frontend instrument search service (D → FR-002, FR-003, FR-014)

**Decision**: New `InstrumentSearchService` (`@Injectable({providedIn:'root'})`):
- `search(query: string): Observable<CatalogListing[]>` → `GET /api/financial-instruments?query=<trimmed>`.
- In the dialog, drive it from the search input's `valueChanges` with
  `debounceTime(250)`, `map(trim)`, `distinctUntilChanged()`, `filter(q => q.length >= 1)`,
  `switchMap(q => service.search(q))` (switchMap cancels the in-flight request).
- **Never** issue a request for a blank/whitespace box (EN004 returns `400` for that); the dialog
  shows its idle/hint state instead.
- On HTTP error → emit an `error` state (the dialog shows a recoverable message + retry); never
  synthesize a listing.
- Response maps to `CatalogListing { id, name, ticker, market, currency, active, isin? }` — the exact
  EN004 `FinancialInstrument` schema. No provider fields exist to leak.

**Rationale**: Mirrors `PortfolioApiService`'s "shape the request, classify the response, backend is
authoritative" style (AR-013). `switchMap` + debounce is the standard Angular typeahead pattern and
gives the "loading / results" states cheaply. Min-length 1 aligns with EN004's `minLength: 1`.

**Alternatives considered**:
- *`exhaustMap` / `concatMap`* — rejected: stale results / backpressure; `switchMap` is correct for
  search-as-you-type.
- *Client-side caching of results* — deferred; the catalog is tiny and served locally, no need.

---

## D6 — The Add Position combobox + state model (D → FR-001, FR-004, FR-007, FR-025)

**Decision**: `add-position.dialog.ts` is reworked. The three `<input formControlName="ticker|market|currency">`
are replaced by:

1. **Instrument search combobox** — a text `<input role="combobox" aria-expanded aria-controls aria-activedescendant>`
   over a `<ul role="listbox">` of `<li role="option">` result rows. Each row:
   `«Name» — «TICKER» · «MIC» · «CCY»` (+ `ISIN «isin»` when present). Keyboard: ↑/↓ move
   `aria-activedescendant`, Enter selects, Esc clears/closes. Visible focus ring (design system §Accessibility).
2. **Selected state** — shows `«Name»` + the chosen `TICKER · MIC · CCY`, with a "change" affordance
   that reopens search.
3. **Listing selector (only when needed — FR-007)** — after an instrument is chosen, if
   `listingsForInstrument(selected).length >= 2`, render a Market/Currency `<select>` (or segmented
   control) whose options are **exactly** those listings (`MIC · CCY`); choosing one sets
   `ticker/market/currency`. With one listing, apply it and skip this control.
4. **Quantity / Initial purchase date / Average purchase price** — unchanged FD001 controls; the
   price label reads `(optional, in «selected CCY»)`.

**Dialog states** (design-system mapping): `idle` (empty box, hint) · `searching` (localized
spinner, §Loading) · `results` (listbox) · `no-results` ("No matching instrument found." + no
proceed path, §Empty/Validation) · `selected` (+ optional listing selector) · `error` (recoverable
message + Retry, §Error). **Add position** button disabled until an instrument (and, when required, a
listing) is selected **and** quantity is a valid number (FD001 rule kept).

**Rationale**: A dialog is the right container (design system §Dialogs; FD001 already uses one). The
ARIA combobox pattern is the accessible, keyboard-first way to do search+select (FD002 §12,
design-system §Accessibility). The listing selector only appears when it adds value (single-listing =
one decision). Everything below the instrument selector is untouched FD001.

**Alternatives considered**:
- *Native `<datalist>`* — rejected: no control over rendering/keyboard/ARIA, can't show the 4-field
  result row.
- *A full-page instrument picker* — rejected (design system: use a dialog for a focused action).
- *Third-party combobox library* — rejected: no new dependency (constitution I / plan); the pattern
  is small and well-documented.

---

## D7 — Instrument grouping key for the listing selector (OD-FD002-2)

**Decision**: Group catalogued listings into "the same instrument" by **normalized `name`** —
`name.trim().toLocaleUpperCase()`. The listing selector for a chosen result offers every catalogued
listing whose normalized name equals the chosen one. ISIN-based grouping is a **future refinement**
(recorded), not implemented now.

**Rationale**: EN004's model is listing-centric — there is **no instrument-level identifier** in the
catalog or the API (`id` is the per-`ticker+market` `ListingId`). `name` is the only field two
listings of one economic instrument reliably share in EN004's normalized data. **EN004's current
curated catalog contains no multi-listing instruments**, so this branch is dead in practice today
and is exercised only by a synthetic frontend unit test — real-world correctness can be revisited if
EN004 later loads dual listings. ISIN is optional and can legitimately differ across venues, so it
is not a safe primary key.

**Alternatives considered**:
- *ISIN when present, else name* — adopt later if needed; adds branching for zero current benefit.
- *Ask EN004 to add an `instrumentGroupId`* — a real EN004 scope change (schema + API); not
  justified by FD002's current need. Flagged in OD-FD002-2 for the human.
- *No grouping — every result is a directly-selectable listing* — this is what EN004's search already
  returns and what the user did **not** pick (they chose the two-step "constrained selectors"). The
  chosen design degrades gracefully to this when an instrument has one listing.

---

## D8 — Wire shape unchanged (D → FR-021, FR-012)

**Decision**: The `POST /api/portfolios` **request body is byte-identical** to FD001 —
`{ name, positions: [{ ticker, market, quantity, currency, initialPurchaseDate?, averagePurchasePrice? }] }`.
FD002's selection just *fills* `ticker/market/currency` from the chosen listing. `PositionDraft`
keeps its shape; the dialog additionally holds a non-serialized `selectedListing` reference for
display and for the listing selector. The only response change is the additive enum value (D4).

**Rationale**: Minimises blast radius, keeps FD001's contract test / persistence semantics intact,
and keeps the guarantee where it must be (server-side, D1) rather than trusting a richer client
payload. No provider field is introduced anywhere (FR-021; EN004 VC-010).

**Alternatives considered**:
- *Send a `listingId` instead of `ticker/market/currency`* — rejected: changes the FD001 request
  contract and the Position identity plumbing; the backend would still resolve it to `ticker+market`
  anyway (BR-009).

---

## D9 — E2E approach + FD001 non-regression (D → FR-028, FR-017)

**Decision**: New `implementation/platform/e2e/tests/FD002-select-instrument.spec.ts` — the
mandatory closure journey, driven through the real containerized stack (browser → nginx → Angular →
`/api` → core-service → PostgreSQL), starting from `/portfolios/new`:

```text
open Create Portfolio → name it → Add position →
type "Apple" (or "AAPL") → results listbox shows "Apple Inc. — AAPL · XNAS · USD" →
select it (keyboard) → dialog shows controlled AAPL / XNAS / USD (no free-text) →
quantity "3" → Add position → Save → "created successfully" confirmation
```

Plus a negative assertion: the ticker/market/currency controls are **not** free-text inputs
(role/readonly check). `syntheticPosition()` in `e2e/support/data.ts` **stays `ASML / XAMS / EUR`**
— that combination is in EN004's catalog (`ASML.AS/AMS → ASML·XAMS·EUR`), so
`FD001-create-portfolio.spec.ts` passes **unchanged** even with the new backend check. The FD002
spec uses `AAPL` (also catalogued) to exercise a USD path.

Backend FD001 ITs: audit their fixtures in `/speckit-tasks` — any that create a position with a
non-catalogued `(ticker, market)` must switch to a catalogued one **or** seed the catalog in the IT.
`PostgresContainerSupport` already sets `app.reference-data.import-on-startup=false`; the FD001 ITs
that need a catalog will seed it via the `financialinstrument` writer port (as
`FinancialInstrumentCatalogAdapterIT` does) or a small SQL insert in the test.

**Rationale**: EN002 §13 — E2E starts from the UI, no test-only endpoint, synthetic unique data,
disposable DB per run. Reusing a catalogued synthetic position is the cheapest way to keep FD001
green.

**Alternatives considered**:
- *Relax the backend check when a feature flag is off* — rejected: the guarantee must always hold
  (FR-011); flags hide regressions.
- *Mock the catalog in the E2E* — rejected: E2E runs the real stack (EN002).

---

## D10 — Test plan (D → FR-026, FR-027, constitution VII)

**Decision**:

| Layer | Tests |
|---|---|
| Unit (backend, TDD) | `CreatePortfolioServiceTest` — with a **fake `InstrumentCatalog`**: (a) all positions selectable → created; (b) one non-catalogued position → `PortfolioValidationException` with `INSTRUMENT_NOT_IN_CATALOG` on `positions[i]`, nothing saved; (c) structural error on pos 0 **and** non-catalogued pos 1 → both violations in one exception; (d) a position missing ticker/market/currency is **not** catalog-checked (only the FD001 `REQUIRED`/`CURRENCY_FORMAT`); (e) the fake is called with `(ticker, market, currency)` — a fake that keys on all three returns `false` for a currency mismatch → `INSTRUMENT_NOT_IN_CATALOG`. |
| Unit (frontend) | `instrument-search.service.spec.ts` — trims, min-length gate, maps response, classifies HTTP error; `add-position.dialog.spec.ts` — idle→search→results→select a **USD** instrument (single listing applies) and a **EUR** instrument→confirm; ≥2 listings show the selector; no-results state; error state; keyboard select; ticker/market/currency are not free-text. |
| Integration (Testcontainers) | `CatalogInstrumentCatalogAdapterIT` — seed catalog: active `AAPL·XNAS·USD`, active `SAN·XMAD·EUR`, an inactive listing, a GBP listing. `isSelectable`: `("AAPL","XNAS","USD")` & `("aapl","XNAS","usd")` → true; `("SAN","XMAD","EUR")` → true (**EUR path**); `("AAPL","XNAS","EUR")` → false (**currency mismatch**); wrong market / inactive / GBP / unknown → false. `FinancialInstrumentCatalogAdapterIT` += `findSelectable` cases (returns the listing incl. its currency). |
| Contract | `CreatePortfolioControllerContractTest` += non-catalogued position → `400` conforms to `openapi.yaml`, `code = INSTRUMENT_NOT_IN_CATALOG`. |
| Architecture | 2 new ArchUnit rules (D11); existing 12 rules stay green (→ 14 total); deliberate-violation spot check in `/speckit-tasks`. |
| E2E | `FD002-select-instrument.spec.ts` (D9); `FD001-create-portfolio.spec.ts` + `platform-smoke.spec.ts` unchanged and green. |
| Regression | full `./mvnw verify` (Surefire + Failsafe + JaCoCo ≥ 90 % line+branch + ArchUnit); `ng test`; `./e2e.sh`. |

**Rationale**: The new rule is deterministic business logic → TDD (constitution VII). Adapters that
touch PostgreSQL → Testcontainers. The contract change → a contract test. Coverage gate unchanged;
the new service branch + adapter are covered by the above.

---

## D11 — ArchUnit rule for the inter-module dependency (D → OD-FD002-4)

**Decision**: Add to `StandardArchitectureRulesTest`:

```java
@ArchTest
static final ArchRule portfolio_uses_financialinstrument_only_through_its_domain_ports =
    noClasses().that().resideInAPackage("..core.portfolio..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "..core.financialinstrument.infrastructure..",
            "..core.financialinstrument.business..");
```

i.e. `portfolio` may reference only `..financialinstrument.domain.ports..` and
`..financialinstrument.domain.model..` (read types), and only from `portfolio.infrastructure`
(the existing `business_does_not_depend_on_infrastructure` + a companion check that
`portfolio.domain`/`portfolio.business` don't touch `financialinstrument` at all).

**Rationale**: Makes AR-006 ("through an explicit application interface") machine-checked and stops
the coupling from drifting into direct table/entity access or business-to-business calls. The rule
is non-vacuous (the new adapter exercises the allowed path).

**Alternatives considered**:
- *No rule, rely on review* — rejected: EN004 set the precedent that module boundaries are
  ArchUnit-enforced.
- *Forbid the dependency entirely, duplicate a check in `portfolio`* — rejected (AR-006 violation,
  data duplication).

**`product/` note**: OD-FD002-4 was confirmed by jaruiz 2026-09-03 and
`architecture-rules.md` now carries **AR-062 — Inter-Module Reads Go Through a Published Port**,
naming this `portfolio → financialinstrument` dependency as an approved instance. The ArchUnit
rules above make it machine-checked (AR-061/AR-062).

---

## D12 — Design-system mapping for the six UI states (D → FR-024, FR-025)

**Decision**: Bind each state to an existing design-system section — no new design tokens or
components:

| State | Design-system anchor | Concrete |
|---|---|---|
| idle | §Forms (read-only/enabled states) | empty search field + one-line hint "Search by ticker or company name" |
| searching | §Loading States | localized spinner in/under the field; the rest of the dialog stays interactive |
| results | §Cards / compact density | `role="listbox"`; ≤ ~8 rows visible, scroll for more; 4-field row |
| no-results | §Empty States / §Validation | "No matching instrument found." — no button to accept the text |
| selected | §Forms (selected/read-only) | name + `TICKER · MIC · CCY`; "Change" link; optional listing `<select>` |
| error | §Error States | "We couldn't search right now. Please try again." + Retry; no stack trace |

Accessibility contract (design-system §Accessibility): semantic `<label>`s, visible focus, keyboard
operable listbox, error text not conveyed by color alone, meaningful-icon alt text.

**Rationale**: FD002 §12 defers visual styling to the global design system; this table is the
binding so `/speckit-tasks` and the component build have a concrete, testable target without
inventing UX.

**Alternatives considered**: a feature-specific UX artifact under
`product/definition/features/FD002-.../ux/` — optional; not required (design system covers it) and
would be a `product/` addition. Skipped unless the human wants wireframes.
