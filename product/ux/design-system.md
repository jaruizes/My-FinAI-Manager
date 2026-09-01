# My-FinAI-Manager — Design System

## Purpose

This document defines the global visual language and UX direction of My-FinAI-Manager.

It establishes reusable principles for:

- visual style;
- layout;
- navigation;
- colors;
- typography;
- spacing;
- cards;
- forms;
- tables;
- charts;
- dialogs;
- responsive behavior;
- shared design tokens.

Feature-specific UX artifacts may refine these rules for a concrete interaction, but they must remain consistent with this global design system.

Visual prototypes and screenshots are references for intended direction and interaction. They do not override approved Feature Definitions, business rules, or acceptance criteria.

---

# Visual Direction

My-FinAI-Manager should use a modern dark financial-dashboard visual style.

The interface should feel:

- professional;
- data-oriented;
- compact;
- modern;
- high-contrast;
- visually clean;
- suitable for prolonged desktop use.

The intended visual direction is similar to a modern dark administrative or financial dashboard with:

- persistent left navigation;
- top application bar;
- dark page background;
- slightly lighter content cards;
- compact information density;
- strong emphasis on numeric values;
- accent colors used mainly to communicate status or importance.

The interface should avoid:

- excessive decoration;
- visually heavy gradients;
- unnecessary animation;
- oversized empty spaces;
- decorative elements without functional value;
- consumer-oriented visual effects that reduce information density.

---

# Layout

The default desktop layout should use three main areas:

```text
┌─────────────────────────────────────────────────────────┐
│                    Top Application Bar                  │
├───────────────┬─────────────────────────────────────────┤
│               │                                         │
│               │              Main Content               │
│   Sidebar     │                                         │
│               │                                         │
│               │                                         │
└───────────────┴─────────────────────────────────────────┘
```

The main layout should prioritize fast access to portfolio information and efficient use of available screen space.

---

# Sidebar

The sidebar is the primary navigation mechanism on desktop.

It should:

- remain visible on standard desktop resolutions;
- provide primary application navigation;
- use icons together with labels where practical;
- visually highlight the active section;
- use a darker tone than the main content area;
- support collapsible navigation groups if required;
- remain compact and avoid unnecessary visual noise.

Possible sections may eventually include:

- Dashboard
- Portfolios
- Analysis
- Risks
- Recommendations
- Market Intelligence
- Settings

The exact navigation structure is governed by implemented product capabilities and may evolve over time.

A visual design must not expose navigation options for capabilities that do not yet exist unless they are intentionally disabled or explicitly part of the approved UX.

---

# Top Application Bar

The top application bar provides secondary global navigation and contextual actions.

It may contain:

- global search;
- primary contextual actions;
- notifications;
- application status;
- user profile access;
- settings or utility actions.

The top bar should remain visually secondary to the portfolio and analytical content.

Primary page actions may appear in the top bar when this improves usability.

---

# Application Background

The application should use a dark neutral visual theme.

Recommended visual direction:

```text
Application background    Near-black / charcoal
Sidebar                   Dark charcoal
Cards / surfaces          Slightly lighter dark gray
Elevated surfaces         Moderately lighter dark gray
Borders                   Subtle low-contrast gray
Primary text              Near-white
Secondary text            Muted gray
```

Avoid pure black for large surfaces where possible.

The theme should preserve enough tonal separation between:

- application background;
- navigation;
- cards;
- dialogs;
- focused controls.

---

# Cards

Cards are a primary information container in My-FinAI-Manager.

Cards should:

- use a dark elevated surface;
- have subtle borders or elevation;
- use a small-to-medium corner radius;
- maintain consistent internal spacing;
- provide clear visual hierarchy;
- emphasize the most important numeric or analytical value;
- avoid excessive shadows or decorative effects.

Typical card structure:

```text
Label / Title

Primary Value
Secondary Metric / Trend

Optional metadata
Optional action
```

Example:

```text
Portfolio Value

45,320.54 €
+2.6%

Today
```

Cards may be used for:

- portfolio valuation;
- daily variation;
- risk indicators;
- concentration;
- investment results;
- market metrics;
- alerts;
- recommendations.

---

# Financial Values

Financial and numerical information is one of the most important visual elements of the application.

Primary monetary values should:

- have stronger visual weight than labels;
- use larger typography where appropriate;
- use consistent decimal formatting;
- use consistent currency formatting;
- be easy to compare across cards and tables;
- align numerically when multiple values are presented together.

Examples:

```text
45,320.54 €
+3.5%
-2.4%
```

Positive and negative changes must be visually distinguishable.

Color must not be the only mechanism used to communicate financial meaning.

Where appropriate, combine color with:

- positive/negative signs;
- directional icons;
- labels;
- semantic text.

---

# Color Semantics

Accent colors should primarily communicate meaning.

## Positive

Used for:

- positive performance;
- favorable change;
- successful operations;
- positive status;
- strengthening signals.

Visual direction: green.

## Negative

Used for:

- losses;
- negative performance;
- critical risk;
- destructive actions;
- weakening signals.

Visual direction: red.

## Warning

Used for:

- warnings;
- attention-required situations;
- medium-severity risk;
- incomplete information.

Visual direction: amber / orange.

## Informational

Used for:

- neutral information;
- contextual emphasis;
- informational status;
- secondary actions.

Visual direction: blue or another defined information accent.

## Primary Action

A consistent brand accent must be used for primary interactive actions.

Examples:

- Create Portfolio
- Save
- Add Position
- Confirm

The exact color token must be centrally defined.

Primary action colors must not be hard-coded independently in feature components.

---

# Color Accessibility

Color combinations should meet appropriate accessibility contrast requirements.

Color must not be the sole method used to communicate:

- success;
- failure;
- positive/negative performance;
- risk severity;
- validation state.

Icons, text, shape, or additional indicators should reinforce semantic meaning.

---

# Typography

Typography should prioritize readability of financial and analytical information.

Use a modern sans-serif typeface suitable for dashboards and data-heavy interfaces.

Avoid decorative fonts.

Recommended hierarchy:

```text
Page Title
Section Title
Card Title / Component Title
Primary Financial Value
Body Text
Secondary Text / Metadata
Labels / Captions
```

Numeric values should receive greater visual emphasis than explanatory labels where appropriate.

Typography should remain consistent across frontend features.

---

# Numeric Typography

Numerical values should be optimized for scanability.

Where supported by the selected font, tabular numerals should be considered for:

- tables;
- financial comparisons;
- aligned monetary values;
- percentage columns.

Numeric formatting should remain consistent throughout the application.

---

# Spacing

Spacing should be consistent and relatively compact.

The application should favor efficient use of screen space without becoming visually crowded.

Use a shared spacing scale instead of arbitrary values.

Conceptually:

```text
spacing-xs
spacing-sm
spacing-md
spacing-lg
spacing-xl
```

Feature implementations should consume shared spacing tokens.

---

# Grid and Content Layout

Pages should use consistent grid behavior.

Dashboard-like screens may use responsive card grids.

Example:

```text
┌─────────────┬─────────────┬─────────────┐
│    Card     │    Card     │    Card     │
├─────────────┴─────────────┼─────────────┤
│                           │             │
│       Main Analysis       │  Secondary  │
│                           │   Content   │
└───────────────────────────┴─────────────┘
```

Information priority should determine card size and placement.

Avoid forcing every piece of information into identically sized cards.

---

# Forms

Forms should follow the same dark visual language.

Inputs should:

- have clearly visible boundaries;
- provide persistent or easily identifiable labels;
- distinguish enabled, disabled, focused, invalid, and read-only states;
- provide validation feedback close to the affected field;
- maintain sufficient contrast;
- use consistent spacing and sizing.

Form design should favor clarity over decoration.

Avoid unnecessary multi-step workflows when a single screen, dialog, or drawer is sufficient.

---

# Validation

Validation feedback should be:

- immediate where useful;
- clear;
- located near the affected control;
- written in understandable language;
- visually distinguishable without relying only on color.

Validation messages should explain how the user can resolve the issue when practical.

---

# Dialogs and Drawers

Dialogs and side drawers may be used for focused operations.

Examples:

- Add Position
- Edit Position
- Confirm Removal
- Configure Portfolio
- Review Recommendation Details

They should:

- preserve the current page context;
- use a clear title;
- expose clear primary and secondary actions;
- make cancellation obvious;
- avoid excessive nested dialogs.

Use a dialog or drawer when the action is focused and does not justify navigating to a full page.

---

# Buttons

Buttons should use a clear hierarchy.

## Primary

Used for the principal action of the current context.

Examples:

- Save
- Create Portfolio
- Confirm

## Secondary

Used for alternative non-destructive actions.

Examples:

- Cancel
- Back
- Add another value

## Destructive

Used for destructive actions.

Examples:

- Delete Portfolio
- Remove Position

Destructive actions should use explicit visual semantics and confirmation where appropriate.

---

# Tables and Lists

Portfolio and financial data will frequently use tables or structured lists.

Tables should support:

- clear column alignment;
- right-aligned numerical values;
- consistent currency formatting;
- consistent percentage formatting;
- readable row separation;
- sorting when meaningful;
- status indicators;
- responsive behavior;
- sufficient contrast.

Important numeric columns should be easy to scan vertically.

Possible columns may include:

```text
Ticker
Market
Quantity
Average Price
Current Price
Value
Variation
Weight
```

Actual columns are feature-specific.

---

# Charts

Charts should follow the global dark visual language.

Charts should:

- prioritize analytical readability over decoration;
- avoid 3D effects;
- avoid excessive visual density;
- use consistent semantic colors;
- provide clear labels and legends when required;
- work correctly on dark surfaces;
- expose meaningful units;
- avoid misleading scales.

Charts must not become the only representation of critical financial information when an accessible textual or numerical alternative is needed.

---

# Status and Trend Indicators

Status indicators should use a consistent semantic system.

Examples:

```text
↑ Positive
↓ Negative
→ Neutral
! Warning
```

The same semantic state should look consistent throughout the application.

---

# Navigation States

Navigation items should have clearly differentiated states:

- default;
- hover;
- active;
- focused;
- disabled.

The active section must be obvious without relying solely on subtle text-color differences.

---

# Responsive Behavior

The primary design target is desktop.

The application should remain usable on smaller screens.

Expected responsive behavior may include:

- collapsible sidebar;
- compact top bar;
- stacked cards;
- responsive grids;
- horizontally scrollable tables where unavoidable;
- full-width dialogs on small screens;
- simplified secondary information.

A dedicated mobile-first experience is not required unless introduced by a Feature Definition.

---

# Accessibility

The design should support:

- sufficient contrast;
- keyboard navigation;
- visible focus states;
- semantic form labels;
- accessible error feedback;
- text alternatives for meaningful icons where needed;
- interaction without relying exclusively on color.

Accessibility requirements should evolve together with product needs and applicable standards.

---

# Design Tokens

Visual properties should be implemented through reusable design tokens.

## Colors

```text
color-background
color-sidebar
color-surface
color-surface-elevated
color-border

color-text-primary
color-text-secondary
color-text-muted

color-primary
color-positive
color-negative
color-warning
color-info
```

## Spacing

```text
spacing-xs
spacing-sm
spacing-md
spacing-lg
spacing-xl
```

## Radius

```text
radius-sm
radius-md
radius-lg
```

## Typography

```text
font-family-primary

font-size-caption
font-size-body
font-size-card-title
font-size-section-title
font-size-page-title
font-size-financial-value

font-weight-normal
font-weight-medium
font-weight-semibold
font-weight-bold
```

## Other

```text
sidebar-width
topbar-height
content-max-width
border-default
focus-ring
```

Components should consume shared tokens rather than introduce arbitrary visual values.

---

# Shared Components

The frontend should gradually establish reusable visual components when repeated patterns emerge.

Possible shared components include:

- App Shell
- Sidebar
- Top Bar
- Card
- Metric Card
- Financial Value
- Percentage Change
- Status Badge
- Alert
- Button
- Dialog
- Drawer
- Form Field
- Table
- Empty State
- Loading State

Reusable components should emerge from actual product needs rather than speculative design-system expansion.

---

# Empty States

Empty states should clearly explain:

- what information is missing;
- why the page is empty;
- what the user can do next.

Example:

```text
You do not have any portfolios yet.

Create your first portfolio to start analysing your investments.

[ Create Portfolio ]
```

---

# Loading States

Loading states should avoid abrupt visual changes.

Use appropriate mechanisms such as:

- skeletons;
- localized spinners;
- progress indicators for long-running operations.

The whole application should not be blocked when only one independent section is loading.

---

# Error States

Errors should be displayed in context.

The interface should distinguish between:

- validation errors;
- recoverable operation errors;
- provider/integration failures;
- system failures.

Technical stack traces or infrastructure details must never be exposed directly to the user.

---

# Feature-Specific UX

Feature-specific visual design should live with the Feature Definition.

Example:

```text
product/definition/features/
└── FD001-create-investment-portfolio/
    ├── feature-definition.md
    └── ux/
        ├── create-portfolio-wireframe.png
        ├── add-position-wireframe.png
        └── prototype-reference.md
```

Feature-specific UX may define:

- screen layout;
- workflow;
- component placement;
- dialog behavior;
- feature-specific visual hierarchy.

It must remain consistent with this global design system.

---

# Visual References

Visual inspiration and screenshots may be stored under:

```text
product/definition/global/ux/references/
```

Reference images define:

- style direction;
- density;
- tone;
- composition;
- visual hierarchy.

They are not pixel-perfect requirements.

A reference screenshot must not silently introduce product functionality that is not defined by approved Feature Definitions.

---

# Relationship with Feature Definitions

Feature Definitions define:

```text
WHAT the user can do
WHAT behavior is expected
WHAT business rules apply
```

This design system defines:

```text
HOW the product should look and feel globally
HOW common interface patterns should behave visually
```

Feature-specific UX defines:

```text
HOW a particular feature materializes that interaction
```

The precedence is:

```text
Business Intent / Feature Definition
              ↓
Global Design System
              ↓
Feature-Specific UX
              ↓
Frontend Implementation
```

Visual design must not override approved business behavior.

---

# Reference Style Summary

The desired My-FinAI-Manager visual style is:

```text
Modern
Dark
Professional
Financial
Data-oriented
Compact
High contrast
Card-based
Sidebar-driven
Desktop-first
Minimal decoration
Strong numerical hierarchy
Semantic status colors
```

The application should feel closer to a professional investment and analytics dashboard than to a consumer banking or lifestyle application.
