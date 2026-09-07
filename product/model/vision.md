# My-FinAI-Manager — Product Vision

## Vision

My-FinAI-Manager is a personal investment portfolio intelligence system designed to help an investor understand, evaluate, and continuously manage one or more investment portfolios.

The system combines portfolio information with market data, financial indicators, news, and other relevant external information in order to provide continuous analysis and actionable portfolio insights.

Its purpose is not limited to showing the current value of a portfolio. My-FinAI-Manager should help the user understand the state of their investments, identify relevant risks, evaluate potential portfolio adjustments, and determine appropriate stop-loss levels for each position.

The system is intended to evolve from an initial portfolio analysis tool into a proactive portfolio-management assistant.

---

## Problem Statement

Individual investors usually have access to large amounts of fragmented information:

* Current portfolio positions
* Market prices
* Company fundamentals
* Financial indicators
* News
* Macroeconomic information
* Analyst opinions
* Market trends
* Geopolitical events

The difficulty is not obtaining information, but determining:

* Which information is relevant to a specific portfolio
* How that information may affect existing positions
* Whether portfolio risk has changed
* Whether a position should be increased, reduced, maintained, or exited
* Whether new investment opportunities should be considered
* Whether the protection level of an investment should change

My-FinAI-Manager aims to continuously connect this information with the user's actual portfolios and transform it into understandable portfolio-level analysis.

---

## Target User

The initial target user is an individual investor who actively manages one or more investment portfolios and wants additional analytical support for investment decisions.

The user remains responsible for all final investment decisions.

My-FinAI-Manager acts as a decision-support system, providing analysis, evaluations, detected risks, recommendations, and supporting evidence.

---

## Product Objectives

My-FinAI-Manager should allow the user to:

* Register and maintain one or more investment portfolios.
* Describe the current composition of each portfolio.
* Analyse the portfolio as a whole and its individual positions.
* Evaluate portfolio composition and detected risks.
* Obtain an assessment of each investment position.
* Receive suggested stop-loss levels for individual positions.
* Understand the reasoning and evidence behind generated conclusions.
* Receive proactive analysis when relevant market conditions change.
* Receive periodic portfolio recommendations.
* Update the portfolio whenever investments change.
* Re-evaluate the portfolio after those changes.

---

# Version 1.0.0

Version 1.0.0 will provide the first complete portfolio-management lifecycle supported by My-FinAI-Manager.

## 1. Portfolio Management

The user must be able to maintain one or more investment portfolios.

For each portfolio, the user must be able to represent its current composition.

The system must support changes such as:

* Adding a new financial instrument
* Increasing an existing position
* Reducing an existing position
* Removing a position
* Updating relevant investment information

The portfolio stored by My-FinAI-Manager must represent the user's current investment state.

---

## 2. Initial Portfolio Analysis

When a portfolio is first incorporated into the system, My-FinAI-Manager must analyse and evaluate it.

The initial analysis should consider both the portfolio as a whole and its individual positions.

The result should provide, at minimum:

* A portfolio assessment
* An assessment of individual positions
* Identified portfolio risks
* Identified position-specific risks
* Relevant observations
* Initial recommendations
* Suggested stop-loss level for each applicable position

The stop-loss recommendation may be represented as:

* A target price
* A percentage relative to a relevant reference price
* Or both

The system should explain the reasoning behind important conclusions and recommendations.

---

## 3. Portfolio Valuation

My-FinAI-Manager must be able to value the portfolio using relevant market information.

The system should provide enough information for the user to understand:

* The current value of the portfolio
* The value of individual positions
* The relative weight of each position
* Relevant portfolio concentrations
* The evolution of investments when sufficient historical information is available

---

## 4. Risk Analysis

The system must analyse risks affecting the portfolio and its positions.

Risk analysis may include areas such as:

* Position concentration
* Sector concentration
* Geographical exposure
* Currency exposure
* Market risk
* Company-specific risk
* Macroeconomic risk
* Regulatory risk
* Geopolitical risk
* Other relevant risks detected from available information

The system should explain why a detected risk may be relevant to the user's portfolio.

---

## 5. Investment Recommendations

My-FinAI-Manager must be capable of providing recommendations related to the user's portfolio.

Recommendations may include:

* Maintain an existing position
* Increase an existing position
* Reduce an existing position
* Exit an existing position
* Consider adding a new financial instrument
* Rebalance portfolio exposure
* Modify an existing stop-loss level

Recommendations should be based on available evidence and should include an explanation of the main factors considered.

A recommendation is advisory and must never automatically execute an investment transaction.

---

## 6. Stop-Loss Management

The system must evaluate appropriate stop-loss protection for applicable portfolio positions.

For each relevant position, My-FinAI-Manager should be able to suggest:

* A stop-loss price
* A stop-loss percentage
* The reasoning behind the proposed level

Stop-loss recommendations must be re-evaluated when relevant portfolio or market conditions change.

As an initial product behaviour, stop-loss recommendations should normally be reviewed periodically, approximately once per month, while still allowing exceptional reassessment when relevant events justify it.

---

## 7. Proactive Portfolio Monitoring

After the initial analysis, My-FinAI-Manager should continuously analyse information that may affect the user's portfolios.

Relevant sources of information may include:

* Market prices
* Market indicators
* Company information
* Financial results
* News
* Macroeconomic indicators
* Sector developments
* Regulatory changes
* Geopolitical events
* Other information considered relevant to the portfolio

The system should determine whether new information is materially relevant to the user's investments.

Relevant changes may trigger a new portfolio assessment or recommendation.

---

## 8. Periodic Portfolio Review

In addition to event-driven analysis, My-FinAI-Manager must periodically review each portfolio.

The initial target frequency is monthly.

A periodic review should reconsider:

* Current portfolio composition
* Position weights
* Detected risks
* Relevant market conditions
* Existing recommendations
* New investment opportunities
* Existing stop-loss levels

The result should clearly identify what, if anything, has changed since the previous assessment.

---

## 9. Portfolio Changes

The user must be able to update a portfolio whenever their actual investments change.

Examples include:

* Buying a new instrument
* Increasing an existing position
* Partially selling a position
* Selling an entire position
* Changing relevant position information

When a portfolio changes, My-FinAI-Manager should reassess the portfolio using its new composition.

---

## 10. Explainability

Important analyses and recommendations should be explainable.

The system should distinguish between:

* Facts
* Calculated information
* Detected relationships
* Interpretations
* Risk assessments
* Recommendations

Where possible, conclusions should reference the information or evidence that contributed to them.

The user should be able to understand why the system produced a particular recommendation rather than receiving an unexplained result.

---

## Product Principles

### Portfolio-centric

All analysis should start from the user's actual portfolio rather than from generic market recommendations.

### Evidence-based

Recommendations and assessments should be based on identifiable data, calculations, or relevant external information.

### Proactive

The system should not require the user to manually request every analysis. Relevant changes should be surfaced proactively.

### Explainable

Important conclusions should include sufficient reasoning to allow the user to understand them.

### Continuously Updated

A portfolio assessment is not permanent. It should evolve as the portfolio and external conditions change.

### Human Decision

My-FinAI-Manager supports investment decisions but does not autonomously execute them.

The final decision always belongs to the user.

---

## Out of Scope for Version 1.0.0

Version 1.0.0 does not aim to provide:

* Automatic execution of buy or sell orders
* Direct brokerage integration for executing transactions
* Fully autonomous portfolio management
* Guaranteed investment performance
* High-frequency trading
* Intraday algorithmic trading
* Social or collaborative portfolio management
* Professional fund-management functionality

These capabilities may be evaluated separately in future versions but are not part of the initial product vision.

---

## Version 1.0.0 Success

Version 1.0.0 should be considered successful when a user can:

1. Register one or more real investment portfolios.
2. Maintain their current composition.
3. Obtain an initial portfolio analysis and valuation.
4. Understand relevant risks affecting the portfolio.
5. Receive explained recommendations for existing and potential investments.
6. Obtain suggested stop-loss levels for relevant positions.
7. Receive periodic and event-driven reassessments based on new information.
8. Update the portfolio and obtain a new assessment reflecting those changes.

At that point, My-FinAI-Manager will provide a complete first version of a continuously evolving personal portfolio intelligence system.
