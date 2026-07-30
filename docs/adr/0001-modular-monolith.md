# ADR 0001: Use a Modular Monolith

- Status: Accepted
- Date: 2026-07-01

## Context

ServicePulse needs authentication, tenant boundaries, incident workflows, audit history, search, background work, observability, and deployment evidence. Splitting these concerns into networked services would add failure modes before the domain boundaries are proven.

## Decision

Build one Spring Boot deployable organized into package-by-feature modules. Modules communicate through application services and identifiers. PostgreSQL provides the transactional boundary.

## Consequences

- Local setup and end-to-end testing remain approachable.
- Transactions can cover incident changes and audit records.
- Module coupling must be reviewed because the compiler does not enforce a network boundary.
- A future split is possible only after measured scaling or ownership pressure; it is not planned for portfolio optics.
