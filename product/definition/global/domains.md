# My-FinAI-Manager — Functional Domains

## Purpose

This document describes the main functional domains of My-FinAI-Manager.

A functional domain groups concepts, responsibilities, and information that belong to the same area of the product.

These domains describe the product from a business and functional perspective.

They do not imply:

- a microservice boundary;
- a deployment unit;
- a database;
- a module;
- a programming-language package;
- or any other technical architecture decision.

Technical boundaries are defined separately in the architecture documentation.

---

# Domain Map

```text
Investor
   │
   ▼
Portfolio Management
   │
   ├──────────────► Financial Instruments
   │
   ├──────────────► Valuation
   │
   ├──────────────► Risk
   │
   ├──────────────► Investment Thesis
   │
   └──────────────► Portfolio Review
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
 Market Intelligence  Recommendations  Stop-Loss
        │
        ▼
   News & Events
```

---

# Portfolio Management

## Purpose

Maintain the portfolios owned by the Investor and their current composition.

## Main Concepts

- Portfolio
- Position
- Portfolio Composition
- Portfolio State
- Portfolio Update

## Information Managed

- Portfolio identity
- Portfolio name
- Positions
- Quantities
- Acquisition information
- Current portfolio composition

## Responsibilities

- Register portfolios
- Add positions
- Increase positions
- Reduce positions
- Remove positions
- Maintain the current state of the portfolio
- Preserve relevant portfolio history when required

## Produces

- Current Portfolio State
- Portfolio changes
- Information required by valuation and analysis

## Related Domains

- Financial Instruments
- Valuation
- Risk
- Portfolio Review
- Investment Thesis

## Outside This Domain

- Market-data acquisition
- Risk calculations
- Investment recommendations
- Stop-loss calculations

---

# Financial Instruments

## Purpose

Represent and identify the investable financial assets used by the product.

## Main Concepts

- Financial Instrument
- Stock
- ETF
- Ticker
- Exchange
- MIC
- ISIN
- Company

## Information Managed

- Instrument identity
- Instrument type
- Trading market
- Instrument identifiers
- Descriptive information
- Relationship between instrument and company

## Responsibilities

- Identify financial instruments consistently
- Maintain canonical instrument information
- Relate instruments to companies and markets
- Support instrument enrichment

## Produces

- Canonical Financial Instrument information
- Instrument metadata used by other domains

## Related Domains

- Portfolio Management
- Market Intelligence
- Valuation
- Risk
- News & Events

---

# Valuation

## Purpose

Determine the financial value of Positions and Portfolios.

## Main Concepts

- Market Price
- Position Valuation
- Portfolio Valuation
- Position Weight
- Currency

## Information Consumed

- Portfolio positions
- Quantities
- Market prices
- Currency information
- Exchange rates when required

## Responsibilities

- Calculate Position Value
- Calculate Portfolio Value
- Calculate Position Weight
- Handle valuation currency rules
- Provide deterministic valuation results

## Produces

- Position valuations
- Portfolio valuation
- Portfolio weights
- Valuation timestamps

## Related Domains

- Portfolio Management
- Market Intelligence
- Risk
- Portfolio Review

---

# Risk

## Purpose

Identify and evaluate risks affecting Positions and Portfolios.

## Main Concepts

- Risk
- Portfolio Risk
- Position Risk
- Concentration Risk
- Sector Risk
- Geographic Risk
- Currency Risk
- Market Risk
- Company-Specific Risk
- Regulatory Risk
- Geopolitical Risk

## Information Consumed

- Portfolio composition
- Valuation
- Instrument information
- Company information
- Market conditions
- News
- Economic information

## Responsibilities

- Identify relevant risks
- Quantify deterministic risks where possible
- Detect semantic or contextual risk relationships
- Explain why a Risk affects a Position or Portfolio
- Track how risk changes over time

## Produces

- Risk assessments
- Risk indicators
- Supporting evidence
- Changes in risk level

## Related Domains

- Portfolio Management
- Financial Instruments
- Market Intelligence
- News & Events
- Recommendations
- Portfolio Review

---

# Market Intelligence

## Purpose

Acquire and interpret market and economic information relevant to the Investor's portfolios.

## Main Concepts

- Market Data
- Market Indicator
- Economic Indicator
- Market Condition
- Relevant Information
- Material Information

## Information Managed

- Current prices
- Historical prices
- Trading information
- Market indicators
- Economic indicators
- Relevant external information

## Responsibilities

- Obtain market information
- Normalize external market data
- Determine relevance to portfolio assets
- Provide market context for analysis

## Produces

- Market observations
- Market indicators
- Relevant market context

## Related Domains

- Financial Instruments
- Valuation
- Risk
- Recommendations
- Portfolio Review
- News & Events

---

# News & Events

## Purpose

Represent and analyse external events and news that may affect the portfolio.

## Main Concepts

- News Item
- Market Event
- Relevant Information
- Material Information
- Evidence

## Information Managed

- News
- Corporate events
- Regulatory events
- Macroeconomic events
- Geopolitical events

## Responsibilities

- Ingest external information
- Detect entities and concepts mentioned
- Determine portfolio relevance
- Distinguish direct and indirect impact
- Identify potentially material events
- Preserve evidence and provenance

## Produces

- Relevant News Items
- Market Events
- Materiality assessments
- Evidence for other domains

## Related Domains

- Financial Instruments
- Market Intelligence
- Risk
- Investment Thesis
- Recommendations
- Portfolio Review

---

# Investment Thesis

## Purpose

Represent the Investor's reasoning behind an investment and evaluate how that reasoning evolves.

## Main Concepts

- Investment Thesis
- Thesis Evidence
- Thesis Risk
- Thesis Assessment

## Information Managed

- Investor rationale
- Assumptions
- Relevant supporting information
- Risks affecting the thesis

## Responsibilities

- Store the Investor's Investment Thesis
- Relate external evidence to the thesis
- Detect information that strengthens or weakens it
- Support future thesis reassessment

## Produces

- Thesis assessment
- Evidence related to the thesis
- Changes in thesis confidence or validity

## Related Domains

- Portfolio Management
- News & Events
- Risk
- Recommendations
- Portfolio Review

---

# Recommendations

## Purpose

Generate advisory conclusions about possible portfolio actions.

## Main Concepts

- Recommendation
- Recommendation Rationale
- Advisory Action
- Investment Opportunity

## Information Consumed

- Portfolio State
- Valuation
- Risk
- Market Intelligence
- News & Events
- Investment Thesis
- Previous recommendations

## Responsibilities

- Evaluate possible actions
- Generate recommendations
- Explain the reasoning
- Preserve supporting evidence
- Distinguish recommendation from execution

## Produces

Recommendations such as:

- Maintain
- Increase
- Reduce
- Exit
- Consider a new investment
- Rebalance

## Related Domains

- Risk
- Portfolio Review
- Stop-Loss Management
- Investment Thesis

## Outside This Domain

- Executing trades
- Sending broker orders

---

# Stop-Loss Management

## Purpose

Evaluate appropriate protective Stop-Loss levels for existing Positions.

## Main Concepts

- Stop-Loss
- Stop-Loss Recommendation
- Stop-Loss Price
- Stop-Loss Percentage
- Stop-Loss Reassessment

## Information Consumed

- Position information
- Market price
- Historical price behaviour
- Volatility
- Risk assessments
- Portfolio context

## Responsibilities

- Calculate or evaluate Stop-Loss levels
- Explain proposed Stop-Loss levels
- Periodically reassess them
- Detect when significant changes justify reassessment

## Produces

- Stop-Loss Recommendation
- Stop-Loss Price
- Stop-Loss Percentage
- Explanation

## Related Domains

- Market Intelligence
- Risk
- Recommendations
- Portfolio Review

---

# Portfolio Review

## Purpose

Coordinate a complete assessment of a Portfolio at a specific point in time.

## Main Concepts

- Portfolio Review
- Initial Portfolio Review
- Periodic Portfolio Review
- Event-Driven Portfolio Review

## Information Consumed

Portfolio Review combines information from:

- Portfolio Management
- Valuation
- Risk
- Market Intelligence
- News & Events
- Investment Thesis
- Recommendations
- Stop-Loss Management

## Responsibilities

- Produce an integrated assessment
- Compare with previous reviews
- Detect meaningful changes
- Provide the Investor with an understandable summary

## Produces

- Portfolio Assessment
- Position Assessments
- Risks
- Recommendations
- Stop-Loss Recommendations
- Relevant changes since previous review

---

# Domain Relationships

The domains collaborate conceptually as follows:

```text
Portfolio Management
        │
        ├────────► Financial Instruments
        │
        ├────────► Valuation
        │
        └────────► Investment Thesis
                         │
Financial Instruments   │
        │                │
        ▼                ▼
Market Intelligence ──► News & Events
        │                │
        └──────┬─────────┘
               ▼
              Risk
               │
        ┌──────┴───────┐
        ▼              ▼
Recommendations    Stop-Loss
        │              │
        └──────┬───────┘
               ▼
        Portfolio Review
               │
               ▼
            Investor
```

---

# Domain Evolution

The domain map is expected to evolve as My-FinAI-Manager grows.

New Feature Definitions may:

- introduce new concepts into an existing domain;
- create relationships between existing domains;
- reveal the need for a new functional domain;
- split an existing domain when its responsibilities become too broad.

Changes to the domain map should be deliberate and reflected in this document.

A Feature Definition should reference the domains it affects but should not silently redefine domain ownership.

---


