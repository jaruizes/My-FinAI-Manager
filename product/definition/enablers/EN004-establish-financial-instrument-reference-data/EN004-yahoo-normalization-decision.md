# EN004 Reference Data Normalization Decision

## Yahoo CSV as Initial Instrument Source

The supplied `reference-data/Yahoo-Finance-Ticker-Symbols.csv` may be used as an **initial import source**, not as the canonical domain model.

The import adapter must persist both:

- the raw provider symbol (`providerSymbol`);
- the normalized canonical ticker (`ticker`).

The Yahoo `Exchange` value is provider-specific and must be translated through
`reference-data/yahoo-exchange-to-mic-mapping.csv`.

The initial FD002 catalog only imports rows whose mapping has:

```text
supported_for_fd002 = true
```

and whose normalized currency is:

```text
EUR
USD
```

Rows with unsupported, legacy, ambiguous, or unresolved exchanges are skipped/quarantined and reported by the importer rather than silently accepted.

## Canonical Market Decision

The canonical `market` stored by My-FinAI-Manager is an ISO 10383 MIC.

Where a venue has an operating MIC and a more specific listing/segment MIC, prefer the **specific listing MIC** when it can be determined reliably.

Example:

```text
Yahoo MCE / .MC
    ↓
XMAD                  canonical listing MIC
BMEX                  operating MIC (optional metadata)
```

This preserves the existing FD001 identity:

```text
ticker + market(MIC)
```

## Ticker Suffix Normalization

Suffixes must **not** be removed with a generic rule such as:

```text
remove everything after the last "."
```

That rule is forbidden because a period may be part of a legitimate symbol and because not every suffix has the same meaning.

Normalization is mapping-driven.

### Algorithm

Given:

```text
rawTicker
sourceExchangeCode
```

1. Trim surrounding whitespace.
2. Convert the provider symbol to the canonical case used by the catalog (uppercase initially).
3. Extract the final `.<suffix>` candidate only for lookup purposes.
4. Check `reference-data/yahoo-exchange-suffix-overrides.csv`.
   - If `(exchange, suffix)` has an explicit override, use its MIC/currency.
5. Otherwise load the exchange rule from `reference-data/yahoo-exchange-to-mic-mapping.csv`.
6. If that rule defines `expected_yahoo_suffix`:
   - strip the suffix **only when** the symbol ends exactly with that configured suffix;
   - otherwise reject/quarantine the row as a mapping mismatch.
7. If the exchange rule has an empty suffix:
   - do not remove any portion of the symbol.
8. Preserve the original value as `providerSymbol`.
9. Persist the stripped value as canonical `ticker`.
10. If the resulting ticker is empty or the MIC/currency cannot be resolved, reject/quarantine the row.

### Examples

```text
SAN.MC + MCE
→ providerSymbol = SAN.MC
→ ticker         = SAN
→ market         = XMAD
→ currency       = EUR
```

```text
IBE.MC + MCE
→ providerSymbol = IBE.MC
→ ticker         = IBE
→ market         = XMAD
→ currency       = EUR
```

```text
AAPL + NMS
→ providerSymbol = AAPL
→ ticker         = AAPL
→ market         = XNAS
→ currency       = USD
```

```text
ADS.DE + FRA
→ suffix override FRA + DE
→ providerSymbol = ADS.DE
→ ticker         = ADS
→ market         = XETR
→ currency       = EUR
```

A symbol without a configured suffix is never truncated merely because it contains a period.

## Import Validation

The importer must reject or quarantine a source row when:

- `Exchange` has no mapping;
- the mapping is not approved for FD002;
- currency is not EUR or USD;
- the configured suffix and provider symbol disagree;
- an ambiguous generic exchange cannot be resolved by suffix;
- canonical MIC cannot be determined;
- canonical ticker becomes empty;
- the normalized `(ticker, MIC)` conflicts unexpectedly with another canonical entry.

Import diagnostics must report counts of:

```text
processed
imported
updated
skippedUnsupportedCurrency
skippedUnsupportedMarket
quarantinedAmbiguous
quarantinedInvalid
```

## Mapping Governance

`reference-data/yahoo-exchange-to-mic-mapping.csv` and
`reference-data/yahoo-exchange-suffix-overrides.csv` are infrastructure reference mappings.

They are not product master data and must not be exposed directly through the public API.

Mappings should be reviewed when the Yahoo source format changes or when a new market is added to FD002 scope.
