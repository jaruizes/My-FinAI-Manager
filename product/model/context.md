# My-FinAI-Manager — Product Context

## Purpose

This document defines the business and functional context in which My-FinAI-Manager operates.

It describes:

* Who interacts with the system
* What information the system receives
* What information the system produces
* Which external information sources may influence its analysis
* The main business concepts involved
* The boundaries and responsibilities of the product

This document is intentionally independent from implementation technologies and architectural design.

---

## System Context

My-FinAI-Manager is a personal investment portfolio intelligence system.

Its primary responsibility is to maintain an up-to-date representation of one or more investment portfolios belonging to a user and continuously analyse those portfolios using financial, market, company, economic, and news information.

The system transforms portfolio data and external information into:

* Portfolio valuations
* Position assessments
* Risk assessments
* Investment recommendations
* Stop-loss recommendations
* Portfolio adjustment recommendations
* Explanations and supporting evidence
* Periodic and event-driven portfolio reviews

My-FinAI-Manager does not autonomously execute investment transactions.

---

# Primary Actor

## Investor

The Investor is the primary user of My-FinAI-Manager.

The Investor:

* Owns and manages one or more investment portfolios
* Provides the initial composition of those portfolios
* Updates portfolios when investments change
* Reviews portfolio analysis
* Reviews detected risks
* Receives investment recommendations
* Receives stop-loss recommendations
* Makes the final decision about whether to act on any recommendation

The Investor remains responsible for all investment decisions.

---

# External Actors and Information Sources

My-FinAI-Manager depends on external information to understand the context surrounding a portfolio.

The specific providers are not defined at product-context level. Different implementations or providers may be used over time.

## Market Data Sources

Provide market information associated with financial instruments.

Possible information includes:

* Current market price
* Historical market prices
* Trading volume
* Market indices
* Volatility information
* Other market indicators

---

## Financial Information Sources

Provide company and financial information that may influence portfolio analysis.

Possible information includes:

* Financial statements
* Earnings reports
* Revenue
* Profitability
* Debt
* Cash flow
* Valuation metrics
* Dividend information
* Company guidance
* Analyst estimates

---

## News Sources

Provide news and other relevant current information.

News may relate directly or indirectly to:

* A financial instrument
* A company
* An industry
* A sector
* A geography
* A commodity
* Regulation
* Geopolitics
* Macroeconomic events

A news item does not need to mention a portfolio position directly to be potentially relevant.

---

## Economic and Market Indicator Sources

Provide information that may influence investment conditions.

Examples include:

* Interest rates
* Inflation
* Employment indicators
* GDP-related information
* Currency exchange rates
* Commodity prices
* Market volatility indicators
* Central-bank decisions
* Market indices

---

## Company and Instrument Reference Sources

Provide descriptive or reference information associated with financial instruments and companies.

Examples include:

* Instrument identifiers
* Company information
* Exchange information
* Sector
* Industry
* Country
* Currency
* Business activity

---

# Product Boundary

My-FinAI-Manager is responsible for maintaining portfolio intelligence.

Its boundary includes:

```text
Portfolio Information
        +
Market Information
        +
Company Information
        +
News
        +
Economic Information
        +
Historical Information
        ↓
My-FinAI-Manager
        ↓
Portfolio Intelligence
```

Portfolio Intelligence may include:

* Valuation
* Exposure analysis
* Risk analysis
* Position assessment
* Portfolio assessment
* Investment recommendations
* Stop-loss recommendations
* Portfolio rebalancing suggestions
* New investment opportunities
* Explanations
* Alerts
* Periodic reviews

---

# Product Responsibilities

## Portfolio Representation

My-FinAI-Manager maintains the current state of each portfolio managed by the user.

The system must understand:

* Which instruments are held
* The quantity held
* Relevant acquisition information
* Current portfolio composition
* Changes made by the investor over time

Historical information may also be maintained when required by specific product features.

---

## Portfolio Valuation

The system determines the value of portfolios and their positions using appropriate market information.

Valuation provides the basis for understanding:

* Portfolio value
* Position value
* Position weights
* Concentrations
* Portfolio evolution

---

## Portfolio Analysis

My-FinAI-Manager evaluates the portfolio from multiple perspectives.

Analysis may include:

* Composition
* Diversification
* Concentration
* Performance
* Exposure
* Correlation
* Risk
* Position quality
* Market context
* Company context

The exact analytical methods are defined by the corresponding product features.

---

## Risk Identification

The system identifies risks that may affect individual positions or the portfolio as a whole.

Possible risk categories include:

* Market risk
* Company-specific risk
* Sector risk
* Industry risk
* Concentration risk
* Currency risk
* Geographical risk
* Macroeconomic risk
* Regulatory risk
* Geopolitical risk
* Liquidity risk
* Event-driven risk

Risk classifications may evolve over time.

---

## Recommendation Generation

The system may produce recommendations related to the portfolio.

Recommendations may include:

* Maintain a position
* Increase a position
* Reduce a position
* Exit a position
* Add a new financial instrument
* Rebalance the portfolio
* Modify a stop-loss level

Recommendations are advisory.

My-FinAI-Manager does not execute the recommendation on behalf of the investor.

---

## Stop-Loss Assessment

The system may evaluate appropriate stop-loss protection for relevant positions.

A stop-loss recommendation may include:

* Suggested stop-loss price
* Suggested percentage
* Supporting reasoning
* Relevant risks or market conditions

Stop-loss recommendations may change over time.

---

## Proactive Monitoring

My-FinAI-Manager monitors relevant information after the initial portfolio analysis.

The system should detect information that may materially affect:

* A position
* Several related positions
* A portfolio exposure
* An investment thesis
* A previously identified risk
* An existing recommendation
* A stop-loss recommendation

Relevant changes may produce a new assessment or alert.

---

## Periodic Review

Portfolios are periodically re-evaluated even when no specific event triggers an analysis.

For Version 1.0.0, the expected default review cycle is approximately monthly.

A periodic review may reassess:

* Portfolio composition
* Market conditions
* Risks
* Recommendations
* Stop-loss levels
* Investment opportunities

---

# Core Domain Concepts

The following concepts form the initial business vocabulary of My-FinAI-Manager.

Detailed definitions belong in `glossary.md`.

## Investor

The person who owns and manages investment portfolios.

---

## Portfolio

A collection of investment positions managed together by the Investor.

An Investor may maintain more than one Portfolio.

---

## Position

A current holding of a Financial Instrument within a Portfolio.

---

## Financial Instrument

An investable financial asset represented in the portfolio.

Version-specific features determine which instrument types are supported.

Examples may include:

* Stocks
* ETFs

Additional instrument types may be introduced later.

---

## Company

A business entity associated with one or more Financial Instruments.

A Company and a Financial Instrument are distinct concepts.

---

## Investment Thesis

The Investor's rationale for owning or considering an investment.

An Investment Thesis may later be evaluated against changing market, company, and external information.

---

## Market Event

An event that may affect one or more financial instruments, companies, sectors, or portfolios.

Examples include:

* Earnings publication
* Dividend announcement
* Acquisition
* Regulatory decision
* Guidance change
* Rating change

---

## News Item

A piece of published information that may contain facts, interpretations, or signals relevant to the portfolio.

---

## Risk

A condition or potential event that may negatively affect a financial instrument, position, or portfolio.

---

## Recommendation

An advisory conclusion produced by My-FinAI-Manager suggesting a possible portfolio action.

---

## Stop-Loss Recommendation

An advisory protection level proposed for an existing position.

---

## Portfolio Review

A structured assessment of the portfolio at a particular point in time.

A review may be:

* Initial
* Periodic
* Event-driven
* Triggered by a portfolio modification

---

# Information Flows

## Initial Portfolio Flow

```text
Investor
   ↓
Portfolio Composition
   ↓
My-FinAI-Manager
   ↓
Portfolio Validation
   ↓
Portfolio Valuation
   ↓
Portfolio Analysis
   ↓
Risk Assessment
   ↓
Recommendations
   ↓
Stop-Loss Assessment
   ↓
Investor
```

---

## Portfolio Update Flow

```text
Investor changes actual investments
        ↓
Investor updates My-FinAI-Manager
        ↓
Portfolio state changes
        ↓
Portfolio is re-evaluated
        ↓
Updated analysis and recommendations
```

---

## Proactive Monitoring Flow

```text
External Information
        ↓
Relevance Analysis
        ↓
Is portfolio materially affected?
        ↓
      Yes
        ↓
Reassessment
        ↓
Updated Risk / Recommendation / Stop-Loss
        ↓
Investor
```

---

## Periodic Review Flow

```text
Review Schedule
      ↓
Current Portfolio
      +
Latest Relevant Information
      ↓
Portfolio Reassessment
      ↓
Compare with Previous Review
      ↓
Updated Recommendations
```

---

# Temporal Context

Portfolio intelligence is time-dependent.

My-FinAI-Manager should distinguish between:

* The current state of a Portfolio
* Historical portfolio states
* The time at which external information was valid
* The time at which an analysis was performed
* The time at which a recommendation was produced

A recommendation that was valid at one point in time may no longer be valid later.

This temporal dimension is important for explaining how portfolio assessments evolve.

---

# Information Confidence

Not all information used by My-FinAI-Manager has the same level of certainty.

The system may work with:

* Verified facts
* Market data
* Deterministic calculations
* Third-party estimates
* Analyst opinions
* News reports
* Inferred relationships
* AI-generated interpretations

The product should preserve the distinction between these types of information when producing important conclusions.

An interpretation or inference must not be presented as a verified fact.

---

# Explainability Context

Recommendations and assessments should be understandable by the Investor.

Where appropriate, My-FinAI-Manager should be capable of answering questions such as:

* Why has this risk been identified?
* Why is this position considered relevant to this news item?
* Why is this portfolio considered concentrated?
* Why has this recommendation changed?
* Why has the suggested stop-loss changed?
* Which information contributed to this conclusion?
* Which part of the result is calculated and which part is interpreted?

Explainability is part of the product experience, not only an implementation concern.

---

# Decision Boundary

My-FinAI-Manager may:

* Analyse
* Calculate
* Evaluate
* Compare
* Detect
* Explain
* Recommend
* Alert

My-FinAI-Manager must not, within Version 1.0.0:

* Place investment orders
* Buy financial instruments
* Sell financial instruments
* Modify broker positions automatically
* Execute stop-loss orders
* Manage money autonomously

The boundary is:

```text
Information
     ↓
Analysis
     ↓
Recommendation
     ↓
HUMAN DECISION
     ↓
External execution
```

---

# Version 1.0.0 Context Boundary

Version 1.0.0 focuses on completing the portfolio-intelligence lifecycle:

```text
Portfolio
    ↓
Understand
    ↓
Value
    ↓
Analyse
    ↓
Assess Risk
    ↓
Recommend
    ↓
Protect
    ↓
Monitor
    ↓
Reassess
```

Detailed functionality will be introduced incrementally through human-authored Feature Definitions.

This context document defines the product environment and vocabulary but does not itself define individual feature requirements.
