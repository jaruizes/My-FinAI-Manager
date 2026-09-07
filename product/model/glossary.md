# My-FinAI-Manager — Product Glossary

## Purpose

This document defines the shared business vocabulary used across My-FinAI-Manager.

Its goal is to provide a consistent meaning for the main product and domain concepts used in:

- Product documentation
- Feature Definitions
- Specifications
- Architecture documents
- Engineering discussions
- AI-assisted development
- User-facing explanations

Terms defined here should be used consistently across the project.

This glossary defines concepts, not implementation structures.

Where a concept is related to an applicable international standard, the relevant ISO standard is indicated for reference. Mentioning an ISO standard in this glossary does **not** automatically make its use mandatory in every feature; mandatory use must be established by the relevant Feature Definition, architecture rule, or product policy.

---

# Relevant ISO Standards

The following standards are especially relevant to the financial and reference-data concepts used by My-FinAI-Manager:

| Standard | Purpose |
|---|---|
| ISO 4217 | Codes for the representation of currencies |
| ISO 10383 | Market Identifier Codes (MIC) for exchanges and trading venues |
| ISO 6166 | International Securities Identification Number (ISIN) |
| ISO 10962 | Classification of Financial Instruments (CFI) |
| ISO 18774 | Financial Instrument Short Name (FISN) |
| ISO 17442-1 | Legal Entity Identifier (LEI) |
| ISO 3166-1 | Codes for countries |
| ISO 8601-1 | Representation of dates and times for information interchange |

Feature Definitions determine which of these standards are mandatory for a particular product capability.

---

# Core Actors

## Investor

The person who owns, manages, and makes decisions about one or more investment portfolios.

The Investor:

- Provides portfolio information
- Updates portfolio composition
- Reviews analyses and recommendations
- Decides whether to act on any recommendation

The Investor remains responsible for all final investment decisions.

**Example**

An investor maintains two portfolios:

- "Long-Term Growth"
- "Dividend Portfolio"

They use My-FinAI-Manager to analyse both, but they decide themselves whether to buy, sell, reduce, or increase any position.

---

# Portfolio Concepts

## Portfolio

A collection of investment positions managed together by an Investor.

A Portfolio represents a coherent set of investments that the Investor wants My-FinAI-Manager to analyse and monitor.

An Investor may own more than one Portfolio.

A Portfolio may evolve over time as positions are added, increased, reduced, or removed.

**Example**

```text
Portfolio: Long-Term Growth

Positions:
- ASML
- Microsoft
- Schneider Electric
- S&P 500 ETF
```

---

## Position

A current holding of a Financial Instrument within a Portfolio.

A Position represents the Investor's exposure to that Financial Instrument at a given point in time.

A Position may include information such as:

- Quantity
- Acquisition information
- Investment Thesis
- Current valuation
- Current weight in the Portfolio

A Position is not necessarily equivalent to an individual purchase transaction.

**Example**

```text
Financial Instrument: ASML
Quantity: 2 shares
Average Acquisition Price: EUR 1,182
```

If the investor bought one ASML share in January and another in March, My-FinAI-Manager may still represent them as one aggregated Position.

---

## Portfolio Composition

The set of Positions that currently belong to a Portfolio.

Portfolio Composition describes what the Investor currently holds.

**Example**

```text
ASML                 18%
Microsoft            16%
Schneider Electric   12%
Iberdrola             9%
S&P 500 ETF          45%
```

These positions and weights form the current Portfolio Composition.

---

## Portfolio State

The representation of a Portfolio at a particular point in time.

A Portfolio State may include:

- Current Positions
- Quantities
- Valuation
- Weights
- Relevant metadata

Portfolio State changes when the Investor modifies the Portfolio or when time-dependent information used for valuation changes.

**Related standard**

When Portfolio State timestamps are represented or exchanged, **ISO 8601-1** is the relevant standard for date and time representation.

**Example**

On 1 September:

```text
ASML: 2 shares
Microsoft: 10 shares
```

After buying one additional ASML share:

```text
ASML: 3 shares
Microsoft: 10 shares
```

The Portfolio State has changed.

---

## Portfolio Update

A change made by the Investor to the represented Portfolio.

Examples include:

- Adding a new Position
- Increasing a Position
- Reducing a Position
- Removing a Position
- Updating relevant investment information

**Example**

The Investor buys six Schneider Electric shares and adds them to the Portfolio.

That action produces a Portfolio Update.

---

# Financial Instrument Concepts

## Financial Instrument

An investable financial asset represented or considered by My-FinAI-Manager.

Examples may include:

- Stock
- ETF

Additional instrument types may be introduced by future product features.

A Financial Instrument is distinct from the Company or entity associated with it.

**Related standards**

- **ISO 6166** defines the International Securities Identification Number (ISIN), a standardized identifier for financial instruments.
- **ISO 10962** defines the Classification of Financial Instruments (CFI) code.
- **ISO 18774** defines the Financial Instrument Short Name (FISN).

These standards address different concerns: identification, classification, and standardized naming.

**Example**

```text
Company: Apple Inc.
Financial Instrument: Apple common stock traded on NASDAQ
Ticker: AAPL
```

Apple Inc. is the Company. AAPL represents a Financial Instrument associated with that Company.

---

## Stock

A Financial Instrument representing ownership participation in a company.

A company may have more than one Stock or listed security.

**Related standards**

Stocks may be classified using **ISO 10962 CFI** codes and identified internationally using an **ISO 6166 ISIN**.

**Example**

Holding 10 shares of Microsoft common stock represents an equity ownership interest in Microsoft.

---

## ETF

Exchange-Traded Fund.

A Financial Instrument representing participation in a fund whose shares are traded on an exchange.

An ETF may provide exposure to:

- Indices
- Sectors
- Geographies
- Commodities
- Investment strategies
- Groups of financial instruments

**Related standards**

ETFs, like other financial instruments, may be:

- identified using an **ISO 6166 ISIN**;
- classified using **ISO 10962 CFI**;
- represented with a standardized short name under **ISO 18774 FISN**.

**Example**

An S&P 500 ETF gives the Investor indirect exposure to hundreds of US companies without holding each Stock individually.

---

## Company

A business entity that may issue or be associated with one or more Financial Instruments.

Company and Financial Instrument are distinct concepts.

For example, analysis may refer to a Company's:

- Business activity
- Financial performance
- Industry
- Competitors
- Geographic exposure
- Risks

while portfolio ownership is represented through a Financial Instrument.

**Related standards**

- **ISO 17442-1** defines the Legal Entity Identifier (LEI) scheme for unambiguous identification of legal entities relevant to financial transactions.
- **ISO 3166-1** may be used when representing a Company's country using standardized country codes.

A Company is not required to have an LEI merely because it exists in the My-FinAI-Manager domain; a Feature Definition must decide whether LEI support is required.

**Example**

```text
Company: ASML Holding N.V.
Industry: Semiconductor Equipment
Country: Netherlands
```

A Position does not directly hold the Company entity. It holds a Financial Instrument associated with that Company.

---

## Ticker

A market symbol used to identify a traded Financial Instrument within a particular trading venue or market context.

A Ticker is not assumed to be globally unique by itself.

**ISO relationship**

There is no single ISO standard that makes a ticker symbol globally unique.

Ticker-based identity may therefore need to be combined with a standardized market identifier such as an **ISO 10383 MIC**, or complemented by an instrument identifier such as an **ISO 6166 ISIN**.

**Examples**

```text
AAPL
MSFT
ASML
IBE
```

The same textual ticker may theoretically exist in different market contexts, which is why Ticker alone should not automatically be assumed to be globally unique.

---

## Exchange

A trading venue or market on which a Financial Instrument may be listed or traded.

The exact representation and identification mechanism for exchanges is defined by the corresponding feature or product rule.

**Related standard**

**ISO 10383** defines Market Identifier Codes (MIC) for identifying exchanges, trading platforms, regulated and non-regulated markets, and trade reporting facilities.

**Examples**

Human-readable names:

```text
NASDAQ
NYSE
Euronext Amsterdam
Bolsa de Madrid
```

Corresponding MIC-style identifiers may be used when a Feature Definition requires standardized exchange identity.

---

## Market Identifier Code (MIC)

A standardized code identifying a market or trading venue.

**Related standard**

MIC is defined by **ISO 10383**.

It can identify places where:

- a Financial Instrument is officially listed;
- a trade is executed;
- trade details are reported.

**Example**

```text
Ticker: ASML
MIC: XAMS
```

The combination may be used by a Feature Definition as part of Financial Instrument identity.

---

## Financial Instrument Identifier

A value or combination of values used to identify a Financial Instrument.

Possible identifiers may include:

- Ticker
- Exchange identifier / MIC
- ISIN
- Other market identifiers

The identification mechanism supported by a specific product version is defined by the corresponding Feature Definition.

**Related standards**

- **ISO 6166 — ISIN**: standardized international financial instrument identifier.
- **ISO 10383 — MIC**: standardized market/trading-venue identifier.
- **ISO 10962 — CFI**: classification, not unique identity.
- **ISO 18774 — FISN**: standardized short name, not unique identity.

**Example**

A feature may decide that the combination:

```text
Ticker: ASML
Exchange MIC: XAMS
```

is sufficient to identify a Financial Instrument in that version of the product.

---

## ISIN

International Securities Identification Number.

A standardized identifier intended to uniquely identify financial instruments and certain referential instruments.

**Related standard**

ISIN is defined by **ISO 6166**.

**Example**

An ISIN can be stored alongside a Ticker and MIC to provide a provider-independent international identifier for a Financial Instrument.

---

## Classification of Financial Instruments (CFI)

A standardized code used to classify financial instruments according to their characteristics.

**Related standard**

CFI is defined by **ISO 10962**.

CFI describes what kind of Financial Instrument something is; it should not be treated as the unique identity of that instrument.

**Example**

A future enrichment feature could use a CFI code to distinguish classes of equity, fund, debt, or derivative instruments more precisely than a simple product-level `STOCK` / `ETF` classification.

---

## Financial Instrument Short Name (FISN)

A standardized human-readable short name for a financial or referential instrument.

**Related standard**

FISN is defined by **ISO 18774**.

**Example**

A future enrichment capability could retain a provider-supplied Display Name while also storing an ISO-standardized FISN when available.

---

# Investment Concepts

## Investment Thesis

The Investor's rationale for owning, considering, increasing, reducing, or otherwise evaluating an investment.

An Investment Thesis may describe:

- Expected business growth
- Competitive advantages
- Market opportunities
- Valuation expectations
- Dividend expectations
- Strategic positioning
- Relevant risks

An Investment Thesis represents the Investor's reasoning and is distinct from a Recommendation generated by My-FinAI-Manager.

**Example**

```text
ASML maintains a dominant position in EUV lithography,
which may allow the company to benefit from long-term
semiconductor demand despite short-term cyclical weakness.
```

That text represents the Investor's Investment Thesis.

---

## Investment Opportunity

A Financial Instrument or investment situation that My-FinAI-Manager identifies as potentially relevant for consideration by the Investor.

An Investment Opportunity does not imply that the Investor should necessarily invest.

**Example**

My-FinAI-Manager detects that the Portfolio has little exposure to healthcare and identifies a healthcare ETF as a potentially relevant Investment Opportunity.

---

## Recommendation

An advisory conclusion generated by My-FinAI-Manager suggesting a possible investment-related action.

Examples include:

- Maintain a Position
- Increase a Position
- Reduce a Position
- Exit a Position
- Add a new Financial Instrument
- Rebalance a Portfolio
- Modify a Stop-Loss

A Recommendation must remain advisory and does not automatically trigger any financial transaction.

**Examples**

```text
MAINTAIN ASML
```

Reason:

```text
Long-term thesis remains valid, although short-term
semiconductor demand presents increased volatility.
```

Or:

```text
REDUCE POSITION
```

Reason:

```text
The position now represents 24% of total Portfolio Value,
creating significant concentration risk.
```

---

## Recommendation Rationale

The explanation of why a Recommendation was generated.

A Recommendation Rationale may include:

- Deterministic calculations
- Market information
- Company information
- Risks
- News
- Economic conditions
- Historical context
- Inferred relationships
- Other supporting evidence

**Example**

```text
Recommendation: REDUCE

Evidence:
- Position weight: 27%
- Internal portfolio limit: 20%
- Volatility increased significantly
- Sector concentration also increased

Interpretation:
The combination of position and sector concentration
has materially increased portfolio risk.
```

---

# Valuation Concepts

## Currency

A monetary unit used to express acquisition prices, valuations, market prices, or other monetary amounts.

**Related standard**

**ISO 4217** defines:

- three-letter alphabetic currency codes;
- three-digit numeric currency codes;
- information about minor currency units.

**Examples**

```text
EUR
USD
GBP
CHF
```

When a Feature Definition requires standardized currency representation, ISO 4217 is the expected reference unless explicitly stated otherwise.

---

## Portfolio Valuation

The process or result of determining the financial value of a Portfolio using relevant market information.

**ISO relationship**

Monetary amounts should have an explicitly identified Currency. **ISO 4217** is the relevant standard when standardized currency codes are required.

**Example**

```text
ASML                 EUR 2,400
Microsoft            EUR 3,500
Schneider Electric   EUR 1,800

Portfolio Value      EUR 7,700
```

---

## Position Valuation

The financial value assigned to an individual Position at a particular point in time.

**ISO relationship**

The valuation Currency may be represented using **ISO 4217**.

**Example**

```text
10 shares × EUR 120 market price = EUR 1,200
```

---

## Portfolio Value

The aggregate financial value of the Positions included in a Portfolio.

The exact calculation rules are defined by the relevant product features.

**ISO relationship**

When represented as a monetary amount, Portfolio Value should carry a Currency identifier; **ISO 4217** is the relevant standard for standardized currency codes.

**Example**

If a Portfolio contains:

```text
Position A: EUR 4,000
Position B: EUR 3,000
Position C: EUR 3,000
```

then:

```text
Portfolio Value = EUR 10,000
```

---

## Position Weight

The relative contribution of a Position to the total Portfolio Value.

It is typically represented as a percentage.

**Example**

A Position worth EUR 2,000 inside a EUR 10,000 Portfolio has a Position Weight of:

```text
20%
```

---

## Acquisition Price

The price associated with acquiring a Financial Instrument.

The exact interpretation may depend on the feature, for example:

- Individual acquisition price
- Average acquisition price

**ISO relationship**

An Acquisition Price should be associated with a Currency. **ISO 4217** is the relevant standard for standardized currency identifiers.

**Example**

The Investor purchases one share at EUR 100.

Its Acquisition Price is EUR 100.

---

## Average Acquisition Price

The average price at which the current holding represented by a Position was acquired.

It may be unavailable if the Investor does not know or provide it.

**ISO relationship**

The associated acquisition Currency may be represented using **ISO 4217**.

**Example**

An Investor purchases:

```text
1 share at EUR 100
1 share at EUR 120
```

The Average Acquisition Price is:

```text
EUR 110
```

---

## Market Price

The observed market price of a Financial Instrument at a specific point in time.

Market Price is time-dependent and originates from external market information.

**Related standards**

- Currency may be represented using **ISO 4217**.
- Observation time may be represented using **ISO 8601-1**.
- The relevant market/trading venue may be identified using **ISO 10383 MIC**.

**Example**

```text
ASML market price at 16:30 CET: EUR 1,215
```

---

# Risk Concepts

## Risk

A condition, exposure, uncertainty, or potential event that may negatively affect a Financial Instrument, Position, or Portfolio.

A Risk may originate from internal or external factors.

**Example**

A Portfolio heavily exposed to semiconductor companies may be vulnerable to a sector-wide downturn.

---

## Portfolio Risk

A Risk affecting the Portfolio as a whole.

Examples may include:

- Concentration
- Correlation between Positions
- Currency exposure
- Sector exposure
- Geographic exposure

**Example**

Four different Positions belong to the technology sector and together represent 65% of the Portfolio.

This creates Portfolio Risk even though no individual Company may currently be experiencing problems.

---

## Position Risk

A Risk primarily associated with an individual Position or its related Financial Instrument or Company.

**Example**

A company's debt increases significantly while free cash flow deteriorates.

That may create additional Position Risk for an investment in that company.

---

## Market Risk

The possibility of losses resulting from general market movements.

**Example**

A broad equity-market decline caused by rapidly increasing interest rates affects many Portfolio Positions simultaneously.

---

## Company-Specific Risk

Risk associated with circumstances affecting a particular Company.

Examples include:

- Financial deterioration
- Operational problems
- Management changes
- Competitive pressure
- Business disruption

**Example**

A major product recall affecting one company primarily creates Company-Specific Risk.

---

## Concentration Risk

Risk created when a significant part of a Portfolio is exposed to the same Position, Company, sector, geography, currency, or other common factor.

**Example**

```text
ASML Position Weight: 31%
```

Even if ASML is considered a strong investment, having 31% of the Portfolio in one Position may represent Concentration Risk.

---

## Sector Risk

Risk arising from significant exposure to a particular economic sector or industry.

**Example**

```text
Technology: 62%
Healthcare: 5%
Utilities: 8%
Other: 25%
```

The Portfolio may have elevated Technology Sector Risk.

---

## Geographic Risk

Risk associated with exposure to particular countries or geographic regions.

**Related standard**

When countries need a standardized machine-readable representation, **ISO 3166-1** defines country codes.

**Example**

Several Companies in the Portfolio depend heavily on revenue generated in China.

A regulatory or economic disruption in China may therefore affect several Positions simultaneously.

---

## Currency Risk

Risk arising from movements between currencies relevant to the Portfolio or its Positions.

**Related standard**

Currencies may be represented using **ISO 4217** codes.

**Example**

An Investor whose reference currency is EUR owns several US Stocks priced in USD.

If USD weakens materially against EUR, the EUR value of those Positions may decline even if their USD market prices remain unchanged.

---

## Macroeconomic Risk

Risk resulting from broader economic conditions.

Examples include:

- Inflation
- Interest rates
- Economic contraction
- Employment conditions

**Example**

Persistent high interest rates may negatively affect highly leveraged companies and growth-company valuations.

---

## Regulatory Risk

Risk arising from changes in laws, regulations, regulatory decisions, or public policy.

**Example**

New export restrictions on advanced semiconductor equipment may affect a company even when its own operational performance remains strong.

---

## Geopolitical Risk

Risk resulting from geopolitical events or tensions that may affect markets, companies, sectors, regions, currencies, or supply chains.

**ISO relationship**

When countries involved in a geopolitical exposure are represented in structured data, **ISO 3166-1** country codes may provide a normalized identifier.

**Example**

A geopolitical conflict affecting semiconductor supply chains may indirectly affect several technology Positions in the Portfolio.

---

## Liquidity Risk

Risk that a Financial Instrument cannot be bought or sold efficiently without materially affecting its price or execution conditions.

**Example**

A small-cap Stock with very low daily trading volume may be difficult to sell quickly at the expected price.

---

# Stop-Loss Concepts

## Stop-Loss

A protection mechanism associated with a Position intended to limit potential losses if the market price reaches a predefined level.

My-FinAI-Manager does not execute Stop-Loss orders in Version 1.0.0.

**Example**

A Stock is currently trading at EUR 100.

The Investor establishes a Stop-Loss at EUR 90.

The intended protection threshold is therefore approximately 10% below the current price.

---

## Stop-Loss Recommendation

An advisory Stop-Loss level proposed by My-FinAI-Manager for a Position.

It may be represented as:

- A target price
- A percentage
- Both

**ISO relationship**

If represented as a monetary price, the Currency may use **ISO 4217**.

**Example**

```text
Current Market Price: EUR 100
Suggested Stop-Loss: EUR 92
Suggested Distance: 8%
```

---

## Stop-Loss Price

The market-price level proposed as the reference for a Stop-Loss.

**ISO relationship**

The associated Currency may be represented using **ISO 4217**.

**Example**

```text
Stop-Loss Price: EUR 92.50
```

---

## Stop-Loss Percentage

The percentage-based protection level associated with a Stop-Loss Recommendation.

The exact reference used to calculate the percentage is defined by the relevant product feature.

**Example**

```text
Reference Price: EUR 100
Stop-Loss Price: EUR 92

Stop-Loss Percentage: 8%
```

---

## Stop-Loss Reassessment

The process of reconsidering an existing Stop-Loss Recommendation because relevant conditions have changed.

It may occur:

- Periodically
- After a Portfolio Update
- After a material market event
- After a relevant risk change

**Example**

Previous recommendation:

```text
Stop-Loss: EUR 90
```

One month later, after the Stock rises significantly and volatility changes:

```text
New Stop-Loss Recommendation: EUR 103
```

---

# Analysis Concepts

## Portfolio Analysis

The evaluation of a Portfolio using its composition together with relevant internal and external information.

Portfolio Analysis may include:

- Valuation
- Risk
- Diversification
- Concentration
- Performance
- Market context
- Company context
- Recommendations

**Example**

A Portfolio Analysis may conclude:

```text
- Technology exposure is high.
- Two Positions account for 48% of Portfolio Value.
- Currency exposure to USD has increased.
- Overall Portfolio risk is currently elevated.
```

---

## Position Analysis

The evaluation of an individual Position and its associated Financial Instrument, Company, risks, and relevant external information.

**Example**

An ASML Position Analysis might consider:

```text
- Current valuation
- Earnings trend
- Semiconductor demand
- China exposure
- Export regulation
- Historical volatility
- Original Investment Thesis
```

---

## Portfolio Assessment

A structured conclusion about the current state of a Portfolio resulting from Portfolio Analysis.

**Example**

```text
Overall Assessment: MODERATE-HIGH RISK

Primary factors:
- Technology concentration
- Large exposure to two individual Stocks
- Elevated geopolitical semiconductor risk
```

---

## Position Assessment

A structured conclusion about the current state of a Position resulting from Position Analysis.

**Example**

```text
Position: ASML
Assessment: POSITIVE LONG TERM / ELEVATED SHORT-TERM RISK
```

---

## Portfolio Review

A structured reassessment of a Portfolio at a particular point in time.

A Portfolio Review may be:

- Initial
- Periodic
- Event-driven
- Triggered by a Portfolio Update

**ISO relationship**

The time associated with a Portfolio Review may be represented using **ISO 8601-1**.

**Example**

A monthly review compares the current Portfolio with the state and recommendations from the previous month.

---

## Initial Portfolio Review

The first Portfolio Review performed after a Portfolio is incorporated into My-FinAI-Manager.

**Example**

After importing a Portfolio for the first time, the system produces:

```text
Valuation
Risk assessment
Position assessments
Recommendations
Initial Stop-Loss recommendations
```

---

## Periodic Portfolio Review

A Portfolio Review performed according to a recurring schedule even when no specific external event has triggered it.

The initial product target is approximately monthly.

**Example**

On the first weekend of each month, My-FinAI-Manager reassesses all active Positions and existing Stop-Loss Recommendations.

---

## Event-Driven Portfolio Review

A Portfolio Review triggered because new information is considered materially relevant to the Portfolio.

**Example**

A company representing 20% of the Portfolio unexpectedly issues a significant profit warning.

The event may trigger a new Portfolio Review without waiting for the next monthly cycle.

---

# External Information Concepts

## Market Data

Observed information describing financial markets or Financial Instruments.

Examples include:

- Prices
- Historical prices
- Trading volume
- Indices
- Volatility indicators

**Related standards**

Depending on the data item, structured market data may reference:

- **ISO 6166 ISIN** for Financial Instrument identification;
- **ISO 10383 MIC** for market/trading venue;
- **ISO 4217** for Currency;
- **ISO 8601-1** for timestamps.

**Example**

```text
ASML closing price
NASDAQ 100 daily return
Daily trading volume
30-day historical volatility
```

---

## Market Indicator

A quantitative measure used to describe or interpret market conditions.

**Examples**

```text
VIX
Moving averages
Market breadth
Relative strength
Volatility
```

The exact indicators used by the product are defined by specific features.

---

## Economic Indicator

A quantitative or qualitative measure describing economic conditions.

Examples include:

- Inflation
- Interest rates
- Employment
- GDP-related indicators

**Example**

A higher-than-expected inflation release may alter market expectations about future interest rates.

---

## News Item

Published information that may be relevant to a Company, Financial Instrument, sector, geography, market, Risk, or Portfolio.

A News Item may contain:

- Facts
- Claims
- Opinions
- Estimates
- Interpretations

Its content must not automatically be treated as verified fact.

**ISO relationship**

Publication and observation timestamps may use **ISO 8601-1**. Countries referenced in structured metadata may use **ISO 3166-1**.

**Example**

```text
"Government announces new semiconductor export restrictions."
```

The article may be relevant to ASML even if the Investor's Portfolio is not explicitly mentioned.

---

## Market Event

An event that may affect one or more Companies, Financial Instruments, sectors, markets, or Portfolios.

Examples include:

- Earnings publication
- Dividend announcement
- Acquisition
- Merger
- Regulatory decision
- Rating change
- Guidance change
- Significant corporate announcement

**ISO relationship**

Event times may be represented using **ISO 8601-1**. Related instruments or markets may reference **ISO 6166 ISIN** and **ISO 10383 MIC** when applicable.

**Example**

```text
ASML publishes quarterly earnings and reduces next-year guidance.
```

---

## Relevant Information

Information determined to have a meaningful relationship with a Portfolio, Position, Financial Instrument, Investment Thesis, Risk, or Recommendation.

Relevant Information may have a direct or indirect relationship with the Portfolio.

**Example**

A news article about European semiconductor regulation may be Relevant Information for an ASML Position even if ASML is not named in the article.

---

## Material Information

Relevant Information considered important enough to potentially influence an assessment, Risk, Recommendation, Stop-Loss Recommendation, or Portfolio Review.

Not all Relevant Information is necessarily Material Information.

**Example**

A minor product announcement may be relevant to Microsoft but not material enough to reconsider the Position.

A major earnings warning may be Material Information.

---

# Evidence and Explainability Concepts

## Fact

Information treated by the system as an externally observed or verified statement.

The origin and reliability of a Fact should be identifiable when relevant.

**Example**

```text
Reported quarterly revenue: EUR 7.5 billion
```

assuming it comes from an authoritative company filing.

---

## Deterministic Calculation

A result produced by a defined calculation that yields the same result for the same inputs.

Examples may include:

- Portfolio Value
- Position Weight
- Concentration percentage

**Example**

```text
Position Value = 10 × EUR 120 = EUR 1,200
```

The same inputs always produce the same result.

---

## Interpretation

A conclusion derived from available information that is not itself a directly observed Fact or Deterministic Calculation.

Interpretations may involve probabilistic or AI-assisted reasoning.

**Example**

```text
The company's reduced guidance may indicate weakening
short-term demand.
```

This is an Interpretation, not a Fact equivalent to the guidance itself.

---

## Inference

A conclusion obtained by reasoning over known information or relationships.

An Inference must not be represented as a verified Fact.

**Example**

Known relationships:

```text
Portfolio → holds ASML
ASML → exposed to semiconductor equipment
Semiconductor equipment → affected by export restrictions
```

Possible Inference:

```text
The Portfolio may be indirectly exposed to the new export restriction.
```

---

## Evidence

Information supporting an Assessment, Risk, Interpretation, or Recommendation.

Evidence may include:

- Facts
- Market Data
- Deterministic Calculations
- Financial information
- News Items
- Market Events
- Economic indicators
- Historical information

**Example**

A recommendation to reduce a Position may cite:

```text
- Position weight: 29%
- Historical volatility increased 35%
- Earnings guidance reduced
- Sector exposure already exceeds target
```

---

## Explainability

The ability of My-FinAI-Manager to provide understandable reasons and supporting information for an important conclusion.

**Example**

Instead of:

```text
Recommendation: REDUCE
```

the product should be capable of explaining:

```text
Recommendation: REDUCE

Main reasons:
1. Position represents 29% of Portfolio Value.
2. Portfolio already has 54% semiconductor exposure.
3. Recent guidance weakened.
4. Short-term volatility increased materially.
```

---

## Confidence

An indication of the degree of certainty associated with an Interpretation, Assessment, or Recommendation.

Confidence is distinct from factual correctness and should not convert uncertain information into a Fact.

**Example**

```text
Interpretation:
Chinese demand may weaken during the next quarter.

Confidence: Medium
```

---

# Temporal Concepts

## Analysis Time

The point in time at which an analysis is performed.

**Related standard**

**ISO 8601-1** defines standardized representations for dates and times used in information interchange.

**Example**

```text
Analysis Time:
2026-08-31T09:30:00+02:00
```

---

## Information Time

The point in time at which a piece of information was observed, published, or considered valid.

**Related standard**

**ISO 8601-1** is the relevant standard for exchanging date/time values.

**Example**

A Portfolio Review performed today may use an earnings report published three days earlier.

The earnings report and the analysis therefore have different timestamps.

---

## Recommendation Time

The point in time at which a Recommendation was produced.

**Related standard**

**ISO 8601-1** may be used for its representation.

**Example**

```text
Recommendation Time:
2026-08-31T10:15:00+02:00
```

---

## Portfolio History

The sequence of relevant historical states or changes of a Portfolio over time.

The exact historical information maintained by My-FinAI-Manager is defined by specific product features.

**ISO relationship**

Historical timestamps may be represented using **ISO 8601-1**.

**Example**

```text
June:
ASML weight = 15%

July:
ASML weight = 19%

August:
ASML weight = 26%
```

This history may later help identify increasing Concentration Risk.

---

# Geography and Reference Data Concepts

## Country

A country associated with a Company, Financial Instrument, market, revenue exposure, risk, event, or other relevant domain concept.

**Related standard**

**ISO 3166-1** defines standardized country codes.

**Examples**

```text
ES — Spain
US — United States
NL — Netherlands
```

A Feature Definition may decide whether country codes or human-readable names are required.

---

## Legal Entity Identifier (LEI)

A standardized identifier for legal entities relevant to financial transactions.

**Related standard**

LEI is defined by **ISO 17442-1**.

LEI identifies a legal entity, not a Financial Instrument.

**Example**

A future company-enrichment feature may associate a Company with its LEI while separately identifying its Financial Instruments using ISINs.

---

# Proactive Intelligence Concepts

## Monitoring

The ongoing observation of external and internal information that may affect a Portfolio.

**Example**

My-FinAI-Manager periodically evaluates new market prices, news, earnings, and macroeconomic events related to Portfolio exposures.

---

## Proactive Analysis

Analysis initiated by My-FinAI-Manager without requiring the Investor to explicitly request it at that moment.

**Example**

A significant earnings warning causes My-FinAI-Manager to reassess a Position automatically.

---

## Alert

A message or notification produced when My-FinAI-Manager identifies information or a change considered sufficiently important to bring to the Investor's attention.

An Alert is not necessarily a Recommendation.

**Example**

```text
Alert:
ASML has fallen 8% following new export-control news.
Your current Position represents 18% of the Portfolio.
```

The system may alert the Investor without yet recommending an action.

---

## Portfolio Rebalancing

A potential adjustment of Portfolio composition intended to alter its allocation or exposure.

My-FinAI-Manager may recommend rebalancing but does not automatically execute it.

**Example**

Current exposure:

```text
Technology: 70%
Healthcare: 3%
Industrials: 12%
Other: 15%
```

A Portfolio Rebalancing Recommendation may suggest reducing technology concentration and increasing exposure to other sectors.

---

# Decision Boundary Concepts

## Advisory Action

A suggested action presented to the Investor for consideration.

The Investor decides whether to execute it.

**Example**

```text
Consider reducing ASML from 25% to approximately 18%
of Portfolio Value.
```

---

## Execution

The actual placement or modification of an investment transaction or order through a broker or other financial intermediary.

Execution is outside the Version 1.0.0 product boundary.

**Example**

Sending an actual sell order for two shares to a broker is Execution.

My-FinAI-Manager may recommend it but does not perform it.

---

## Human Decision

The final investment decision made by the Investor after considering available information and recommendations.

My-FinAI-Manager supports but does not replace this decision.

**Example**

```text
My-FinAI-Manager:
Recommendation → REDUCE

Investor:
Decision → MAINTAIN
```

The Investor's decision takes precedence.

---

# Terminology Rules

The following terminology rules apply across the project:

1. **Portfolio** and **Position** must not be used interchangeably.
2. **Company** and **Financial Instrument** must be treated as distinct concepts.
3. **Fact**, **Deterministic Calculation**, **Interpretation**, **Inference**, and **Recommendation** must remain distinguishable.
4. **Risk** identifies potential adverse exposure; it is not itself a Recommendation.
5. **Alert** identifies something requiring attention; it does not necessarily imply an investment action.
6. **Investment Thesis** represents Investor reasoning; **Recommendation** represents system-generated advisory analysis.
7. **Stop-Loss Recommendation** is advisory; **Stop-Loss execution** is outside the Version 1.0.0 boundary.
8. AI-generated interpretations must not be described as verified Facts.
9. Feature Definitions may refine these concepts but should not redefine them inconsistently without an explicit product-level decision.
10. Examples in this glossary are illustrative and must not be interpreted as product requirements unless explicitly established by a Feature Definition.
11. A reference to an ISO standard in this glossary describes the relevant standard for that concept but does not by itself make the standard mandatory for every feature.
12. When a Feature Definition mandates a standardized identifier or representation, it should reference the applicable ISO standard explicitly rather than duplicating the standard's definition.
