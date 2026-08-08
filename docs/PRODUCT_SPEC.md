# ServicePulse Product Specification

Status: Active design document
Last updated: 2026-07-14

Current evidence boundary:

- Local backend evidence is 71 tests with the recorded coverage gate.
- Local frontend evidence is 20 tests with automated axe coverage across 12
  route states, including workspace membership and failed-notification
  operations administrator/viewer states.
- Local OpenAPI evidence is 19 implemented paths with bearer/public security
  boundaries.
- Browser workflow evidence uses the labeled H2 development substitute, not a
  PostgreSQL runtime.
- Docker/PostgreSQL runtime, remote CI, public repository links, screenshots,
  demo media, and deployment remain unverified or unavailable.

## Problem

Development teams need a reliable, searchable record of owned services and incident decisions. Generic task trackers can capture individual tickets but often obscure incident lifecycle, severity, assignments, timelines, and audit history.

## Users

- Workspace administrators manage membership, roles, and services.
- Responders declare and update incidents, write timeline entries, comment, and take assignments.
- Viewers inspect service and incident information without changing operational state.

All organizations, services, users, and events in this project are fictional or synthetic.

## Goals

- Make incident state and ownership understandable at a glance.
- Preserve an append-only record of material changes.
- Enforce workspace boundaries and role permissions.
- Support useful search, filtering, and pagination.
- Make health, logs, metrics, setup, tests, and limitations easy to inspect.

## Non-goals

- Replacing an enterprise paging provider.
- Guaranteeing security, uptime, or regulatory compliance.
- Reproducing any employer system or workflow.
- Splitting the system into microservices for portfolio optics.
- Claiming real customers, traffic, or production outcomes.

## Core entities

| Entity | Purpose |
| --- | --- |
| User | Authenticated person with a disabled/enabled state |
| Workspace | Tenant boundary for services and incidents |
| Membership | User role within a workspace |
| Managed service | Owned software/service registered in a workspace |
| Incident | Severity, state, assignment, timestamps, and description |
| Incident event | Append-only lifecycle or assignment event |
| Comment | Human discussion attached to an incident |
| Audit entry | Security- and administration-relevant change record |

## Incident workflow

Initial statuses:

`OPEN -> INVESTIGATING -> IDENTIFIED -> MONITORING -> RESOLVED`

Allowed recovery paths:

- `OPEN -> RESOLVED` for quickly invalidated or immediately resolved reports.
- `INVESTIGATING -> MONITORING` when a mitigation is applied before a root cause is confirmed.
- `IDENTIFIED -> RESOLVED` for an immediate confirmed fix.
- `MONITORING -> INVESTIGATING` when symptoms return.

`RESOLVED` is terminal in the first release. Reopening will require a separate linked incident so history remains clear.

## First vertical slice

- Public operational status.
- Secure-default API boundary.
- Incident declaration by administrator or responder.
- Incident retrieval by authenticated workspace roles.
- Validation and problem responses.
- PostgreSQL-first schema and migration.
- Unit and API tests.

## Authentication and membership slice

- Database-backed enabled users with BCrypt password verification.
- Public self-registration that creates an enabled user without automatic
  workspace membership.
- Short-lived HMAC-signed access tokens.
- Opaque random refresh tokens stored only as SHA-256 hashes.
- Refresh rotation and logout revocation.
- Configurable local failed-login throttling that preserves generic
  invalid-credentials responses.
- Development-mode seed data that requires an environment-supplied password.
- Workspace membership checks for incident declaration and retrieval.
- Cross-workspace denial tests.

## Incident history slice

- Validated status-transition endpoint using the incident state machine.
- Administrator/responder mutation authorization and viewer read-only authorization.
- Chronological incident event timeline.
- Declaration and status-change audit records.
- Transactional writes so invalid transitions append neither an event nor an audit entry.
- Application repositories expose no delete operation for event or audit records; database-level immutability remains future work.

## Incident collaboration slice

- Idempotent assignment and unassignment endpoints.
- Assignment targets restricted to enabled administrators/responders in the incident workspace.
- Validated comments with a 4,000-character API limit.
- Administrators/responders may add comments; viewers may read them.
- Assignment and comment changes append timeline and audit records transactionally.
- Timeline entries reference comment IDs rather than copying comment bodies.

## Incident query and dashboard slice

- Workspace-scoped incident collection.
- Case-insensitive title/summary search with escaped SQL wildcard characters.
- Optional service, status, and severity filters.
- Zero-based pagination capped at 100 records with stable declaration-time/ID ordering.
- Dashboard total, active, and unassigned-active counts.
- Complete status and severity maps, including zero-count categories.
- Viewer read access and cross-workspace denial tests.

## Managed-service administration slice

- Workspace administrators create services with immutable validated slugs.
- Workspace members list and retrieve services in deterministic name/ID order.
- Administrators update name, optional description, and lifecycle.
- Repeated identical updates do not create additional audit history.
- Database uniqueness remains the final protection against duplicate workspace slugs.
- Service listing uses zero-based pagination capped at 100 records with stable name/ID ordering.
- Service deletion remains future work while incidents can reference service history.

## Logging and request-correlation slice

- Logstash-format JSON console output using Spring Boot's native formatter.
- Valid client `X-Request-ID` values are preserved; missing/invalid values become UUIDs.
- Request IDs are returned to clients and added to MDC for in-request logs.
- Completion events include method, path, status, and elapsed milliseconds.
- The filter does not log query strings, bodies, cookies, or authorization headers.
- MDC cleanup is tested to prevent request-context leakage across reused threads.

## Membership administration slice

- Current members list workspace users, roles, enabled state, and join time.
- Administrators add existing enabled users with an explicit role.
- Administrators change roles or remove memberships.
- Duplicate adds return conflict; missing update/removal targets return not found.
- Identical role updates do not create duplicate audit history.
- Ordered row locks serialize membership mutations.
- Last-enabled-administrator demotion/removal is rejected.
- User invitations remain future work.
- Self-registered users must be added to a workspace by an administrator before
  they receive workspace access.
- Authenticated users discover only their own workspace memberships through `/api/v1/workspaces`.

## Background notification slice

- Declaration and status-change outbox rows share the incident transaction.
- Bounded due-job claims with processing state and attempt count.
- Stable notification ID passed to adapters as an idempotency key.
- Configurable maximum attempts, retry base, claim timeout, and poll cadence.
- Exponential retry delay capped at one hour.
- Stale processing claims are recoverable.
- Exhausted jobs enter a retained `FAILED` state.
- Only the local structured-log adapter is implemented; external delivery is not claimed.
- Failed jobs retain an explicit failure timestamp.
- Current workspace administrators can inspect failed jobs through a
  tenant-scoped, newest-first page capped at 100 records.
- The operator response exposes identifiers, event type, attempts, sanitized
  error class, and timestamps; it excludes incident content and credentials.
- Current workspace administrators can replay retained failed jobs. Replay
  clears failure fields, resets attempts, preserves the job ID as the
  downstream idempotency key, returns the row to `PENDING`, and writes an audit
  entry. It does not deliver externally inside the operator request.

## Browser client slice

- Development-account sign-in, token refresh, session restoration, and sign-out.
- Current-user workspace discovery with role-aware controls.
- Dashboard totals and incident search/filter/pagination.
- Incident declaration, detail, transition, assignment, comments, and timeline.
- Loading, empty, success, error, validation, viewer, and not-found states.
- Responsive, keyboard-oriented layout with route focus and reduced motion.
- Automated route/interaction/HTTP-boundary tests and selected axe coverage.
- Real-browser verification against the API with the H2 development substitute.

## Later slices

1. Manual physical-keyboard, contrast, zoom, and screen-reader checks.
2. Fresh screenshots and demo media captured from current builds.
3. Docker/PostgreSQL/CI/deployment and verified health checks.
4. Invitations, external notification delivery, distributed abuse controls,
   signing-key rotation, and public separate-origin token-transport review.

## Acceptance criteria

Each completed slice must:

- Reject invalid input with a consistent problem response.
- Test authorization boundaries.
- Preserve tenant boundaries.
- Use migrations from an empty database.
- Avoid secrets and default production credentials.
- Record the exact verification command and result.
- Document incomplete behavior without fake responses.

No feature may be described as publicly available, deployed, PostgreSQL-backed,
or remotely CI-verified until the corresponding evidence is executed and
recorded in the control reports.
