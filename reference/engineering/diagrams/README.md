# Architecture Diagrams

This directory contains the version-controlled architecture diagrams for My-FinAI-Manager.

## Format

Architecture diagrams are maintained as Markdown files containing Mermaid diagrams.

This format is preferred because it is:

- text-based;
- versionable in Git;
- easy to review in pull requests;
- renderable by GitHub and many Markdown tools;
- easy for humans and AI agents to evolve;
- independent from proprietary diagramming tools.

## Diagrams

- `system-context.md` — external actors, platform boundary, and external providers.
- `containers.md` — high-level logical application/container topology.

Additional diagrams should be added only when they provide useful architectural information.

Possible future diagrams include:

- `components/<component-name>.md`
- `deployment.md`
- `data-flow.md`
- `async-interactions.md`

Diagrams must reflect the currently approved architecture, not speculative future-state infrastructure.
