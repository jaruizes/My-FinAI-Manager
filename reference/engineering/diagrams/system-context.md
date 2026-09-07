# My-FinAI-Manager — System Context Diagram

## Purpose

This diagram shows My-FinAI-Manager in its external business context.

It identifies the main user and the external systems or information providers with which the platform may interact.

This is a logical product-level view. It does not imply a particular deployment topology.

```mermaid
flowchart LR
    Investor["Investor"]

    Frontend["My-FinAI-Manager
Web Application"]

    Platform["My-FinAI-Manager
Platform"]

    Market["Market Data
Providers"]
    News["News & Financial
Information Providers"]
    Economy["Economic & Market
Indicator Providers"]
    AI["External AI / LLM
Providers"]
    Identity["Identity Provider"]

    Investor -->|"Uses"| Frontend
    Frontend -->|"Business operations"| Platform

    Platform -->|"Market prices,
historical data,
reference data"| Market
    Platform -->|"News, company information,
financial events"| News
    Platform -->|"Economic and
market indicators"| Economy
    Platform -->|"AI-assisted reasoning,
classification, extraction,
summarization"| AI

    Investor -.->|"Authentication"| Identity
    Frontend -.->|"Authentication / tokens"| Identity
    Platform -.->|"Identity validation"| Identity
```

## Notes

- The Investor is the primary actor.
- External providers are represented as logical capabilities rather than named vendors.
- Concrete providers may change over time.
- AI / LLM access must remain provider-neutral from the perspective of core business capabilities.
- My-FinAI-Manager does not execute investment transactions in Version 1.0.0.
