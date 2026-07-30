# ServicePulse Learning Path

Status: Living checklist.
Last updated: 2026-07-14

The candidate should understand each topic before claiming confidence with ServicePulse.

Current evidence boundary:

- Local evidence includes 68 backend tests, 20 frontend tests, 12 automated
  axe route states, 19 OpenAPI paths, and browser workflow verification against
  the labeled H2 development substitute.
- Do not claim PostgreSQL runtime verification, Docker execution, remote CI,
  public repository availability, screenshots/demo media, or deployment until
  those gates are executed and recorded.

## Foundation

- [ ] Java records, enums, exceptions, UUIDs, and `Instant`.
- [ ] Spring dependency injection and configuration.
- [ ] Maven lifecycle: test, package, integration-test, verify.
- [ ] HTTP methods, status codes, headers, and JSON.

## Domain design

- [ ] Aggregate invariants and state transitions.
- [ ] Idempotent PUT semantics and immutable resource identifiers.
- [ ] Application service versus controller versus repository.
- [ ] Optimistic locking.
- [ ] Tenant/workspace boundaries.
- [ ] Append-only event and audit records.
- [ ] Transactional outbox, at-least-once delivery, and idempotency keys.

## Persistence

- [ ] PostgreSQL primary/foreign keys, unique constraints, checks, and indexes.
- [ ] Flyway versioned migrations.
- [ ] JPA entity lifecycle and transaction boundaries.
- [ ] Stable pagination, JPA specifications, and query planning.
- [ ] SQL `LIKE` wildcard escaping and tenant-first predicates.
- [ ] Aggregate SQL, grouped counts, and empty-result behavior.
- [ ] Unique constraints, preflight checks, and duplicate-write races.
- [ ] Why H2 compatibility mode is not PostgreSQL.

## Security

- [ ] Authentication versus authorization.
- [ ] Password hashing and timing-safe verification.
- [ ] Access and refresh tokens.
- [ ] Refresh-token rotation and revocation.
- [ ] Method security and workspace membership checks.
- [ ] Pessimistic row locking, lock ordering, and last-admin invariants.
- [ ] IDOR, CORS, CSRF, local versus distributed rate limiting, and sensitive error handling.

## Testing

- [ ] Pure unit tests.
- [ ] Spring MVC/MockMvc tests.
- [ ] Security test contexts.
- [ ] Testcontainers lifecycle and service connections.
- [ ] Migration-from-empty verification.
- [ ] Critical-path end-to-end tests.
- [ ] React Testing Library queries, user-event interaction, and async UI state.
- [ ] Mocking an HTTP boundary without coupling tests to component internals.
- [ ] Automated axe scans versus manual keyboard and screen-reader checks.

## Frontend

- [ ] React state, effects, memoization, and context boundaries.
- [ ] TypeScript discriminated unions and typed API contracts.
- [ ] Client-side routing, route focus, and not-found behavior.
- [ ] Loading, empty, success, validation, unauthorized, and failure states.
- [ ] Responsive CSS, visible focus, semantic forms, and reduced motion.
- [ ] In-memory access tokens, tab-scoped refresh tokens, XSS, CSRF, and CORS
  trade-offs.

## Operations

- [ ] Health versus readiness.
- [ ] Structured logging and correlation IDs.
- [ ] MDC lifecycle, servlet filter ordering, and request-ID validation.
- [ ] Metrics, labels, and cardinality.
- [ ] Retry backoff, stale claims, dead-letter state, and worker batch limits.
- [ ] Safe operator read models, sanitized failure data, and audited replay
      state transitions.
- [ ] Docker image layers and non-root execution.
- [ ] CI checks, artifacts, deployment health, and rollback.

## Demonstration readiness

- [ ] Explain the project in two minutes without reading.
- [ ] Draw the architecture and request flow.
- [ ] Show one failure, diagnose it, and explain the fix.
- [ ] Explain every current limitation without overstating completion.
- [ ] Say clearly which evidence is local and which gates remain blocked by
      Docker, GitHub publication, deployment, screenshots/media, or manual
      accessibility checks.
