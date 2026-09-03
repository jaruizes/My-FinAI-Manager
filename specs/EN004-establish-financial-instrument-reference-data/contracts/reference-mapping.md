# Contract — Yahoo reference-mapping files & normalization

Infrastructure reference configuration (decision doc §"Mapping Governance"). **Not** product master
data; **never** exposed through the public API. During implementation these two files are **copied**
from `product/definition/enablers/EN004-establish-financial-instrument-reference-data/reference-data/`
into `implementation/platform/backend/core-service/src/main/resources/reference-data/` — the
implementation copy is the runtime authority (OD-EN004-20). Loaded once at startup by
`infrastructure.reference.mapping`.

---

## 1. `yahoo-exchange-to-mic-mapping.csv`

Header: `source,source_exchange_code,expected_yahoo_suffix,canonical_mic,country_iso2,currency,supported_for_fd002,rows_in_supplied_csv,market_name_or_note`

| Column | Use in normalization |
|---|---|
| `source_exchange_code` | **key** — the Yahoo `Exchange` value on the instrument row |
| `expected_yahoo_suffix` | the suffix (without the dot) that this exchange's symbols carry; **empty** = symbols carry no suffix and must not be truncated |
| `canonical_mic` | the ISO 10383 MIC to store as `market` (may be empty for generic/ambiguous codes → resolve via override or quarantine) |
| `country_iso2` | stored on the `market` row (`country_iso2`) |
| `currency` | candidate currency (may be empty → must be resolved by an override or the row is quarantined) |
| `supported_for_fd002` | `true` ⇒ eligible for the selectable catalog; `false` ⇒ `NOT_SUPPORTED_FOR_FD002` |
| `rows_in_supplied_csv`, `market_name_or_note` | informational only — **must not** influence normalization logic; `market_name_or_note` may seed `market.name` if `markets.csv` lacks the row |

Loaded as `Map<String, ExchangeRule>` keyed by `source_exchange_code`.

Notable rows (from the committed file):

- `NMS`, `NGM`, `NCM` → `XNAS` / `USD` / supported, **empty suffix** (US symbols carry no suffix).
- `NYQ` → `XNYS`, `PCX` → `ARCX`, `ASE` → `XASE` — US, empty suffix, supported.
- `MCE` → `XMAD` / `EUR` / supported, `expected_yahoo_suffix = MC`.
- `FRA` → `XFRA` / `EUR` / supported, `expected_yahoo_suffix = F` — **but** `.DE`-suffixed FRA rows
  are redirected to `XETR` by the override table.
- `GER` → `XETR`, suffix `DE`.
- `LSE` → `XLON` / `GBP` / **not supported** (GBP excluded by the EUR/USD filter) — a GBP `LSE`
  row therefore ends as `UNSUPPORTED_CURRENCY`.
- `IOB` → `ILSE` / `USD` / supported (admitted only because currency passes).
- `ENX` → empty MIC / empty currency / not supported — **generic Euronext**; resolvable only via
  the suffix-override table, otherwise `AMBIGUOUS_EXCHANGE`.
- `EUX`, `MDD`, `MAD`, `OBB`, `PNK` → not supported / unresolved — excluded initially.

---

## 2. `yahoo-exchange-suffix-overrides.csv`

Header: `source_exchange_code,yahoo_suffix,canonical_mic,currency,country_iso2,note`

| Column | Use |
|---|---|
| `source_exchange_code` + `yahoo_suffix` | **composite key** `(exchange, suffix)` |
| `canonical_mic` | the MIC to use when this `(exchange, suffix)` pair is seen — **overrides** the exchange rule's `canonical_mic` |
| `currency` | currency to use (may be empty → still unresolved → quarantine) |
| `country_iso2`, `note` | informational |

Committed rows:

- `(FRA, DE) → XETR / EUR` — "supplied CSV has FRA rows with a `.DE` suffix; treat the suffix as
  authoritative and normalize to Xetra."
- `(ENX, PA) → XPAR / EUR`, `(ENX, AS) → XAMS / EUR`, `(ENX, BR) → XBRU / EUR`,
  `(ENX, LS) → XLIS / EUR` — generic Euronext resolved by suffix.
- `(ENX, NX) → XEUR / <empty currency>` — **currency cannot be inferred** ⇒ the row is
  `AMBIGUOUS_EXCHANGE` (quarantined) even though a MIC is present.

Loaded as `Map<ExchangeSuffixKey, SuffixOverride>`.

---

## 3. Normalization contract (from `EN004-yahoo-normalization-decision.md` §"Algorithm")

Input: `rawSymbol` (Yahoo `Ticker`), `sourceExchangeCode` (Yahoo `Exchange`).
Output: `Accepted(providerSymbol, ticker, mic, currency)` **or** `Rejected(reason, detail)`.

```
1.  s := trim(rawSymbol)
2.  providerSymbol := s ; SYM := upper(s)
3.  suffixCandidate := text after the last '.' in SYM, if any
4.  if override(sourceExchangeCode, suffixCandidate) exists:
        mic := override.canonical_mic
        currency := override.currency        # may be empty
        strip := (suffixCandidate present)   # remove ".<suffixCandidate>"
    else:
5.      rule := exchangeRule(sourceExchangeCode)
        if rule missing            -> Rejected(NO_MAPPING)
        if rule.supported == false:
            # the currency is the more informative disqualifier when it is resolvable
            if rule.currency resolvable and not in {EUR, USD} -> Rejected(UNSUPPORTED_CURRENCY)   # e.g. GBP LSE
            else                                              -> Rejected(NOT_SUPPORTED_FOR_FD002) # e.g. EUX
        mic := rule.canonical_mic
        currency := rule.currency
6.      if rule.expected_yahoo_suffix non-empty:
            if SYM endsWith ("." + rule.expected_yahoo_suffix): strip := true
            else                                              -> Rejected(SUFFIX_MISMATCH)
7.      else: strip := false                                   # never truncate
8.  ticker := strip ? SYM without the trailing ".<suffix>" : SYM
9.  # generic/unresolved guards
    if mic is empty     -> Rejected(AMBIGUOUS_EXCHANGE)        # e.g. ENX with no matching override
    if currency is empty -> Rejected(AMBIGUOUS_EXCHANGE)       # e.g. (ENX, NX)
    if currency not in {EUR, USD} -> Rejected(UNSUPPORTED_CURRENCY)
    if mic not present as a Market row -> Rejected(MIC_UNRESOLVED)   # checked by the importer after Markets load
10. if ticker is empty -> Rejected(EMPTY_TICKER)
11. Accepted(providerSymbol, ticker, mic, currency)
```

Then, in `ImportReferenceDataService`: if two `Accepted` rows in the same run resolve to the same
`(ticker, mic)` with a conflicting `name` → `Rejected(IDENTITY_CONFLICT)` for the second.

**Forbidden**: any code path that removes text after the last `.` without an override or a matching
`expected_yahoo_suffix` (spec FR-016; SC-004). A symbol whose exchange rule has an empty suffix is
**never** truncated, even if it contains a period.

### Worked examples (unit-test matrix — decision doc §"Examples")

| rawSymbol | Exchange | outcome |
|---|---|---|
| `SAN.MC` | `MCE` | `Accepted(SAN.MC, SAN, XMAD, EUR)` |
| `IBE.MC` | `MCE` | `Accepted(IBE.MC, IBE, XMAD, EUR)` |
| `AAPL` | `NMS` | `Accepted(AAPL, AAPL, XNAS, USD)` |
| `ADS.DE` | `FRA` | `Accepted(ADS.DE, ADS, XETR, EUR)` — via `(FRA, DE)` override |
| `VOD.L` | `LSE` | `Rejected(UNSUPPORTED_CURRENCY)` — GBP |
| `XYZ.NX` | `ENX` | `Rejected(AMBIGUOUS_EXCHANGE)` — `(ENX, NX)` has no currency |
| `FOO.XX` | `MCE` | `Rejected(SUFFIX_MISMATCH)` — expected `.MC` |
| `.MC` | `MCE` | `Rejected(EMPTY_TICKER)` |
| `BAR` | `EUX` | `Rejected(NOT_SUPPORTED_FOR_FD002)` |
| `WHATEVER` | `ZZZ` | `Rejected(NO_MAPPING)` |
