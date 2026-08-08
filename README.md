# ServicePulse

ServicePulse is an independent incident-management and service-reliability portfolio project for development teams. It is designed as a modular monolith so business boundaries remain visible without adding unnecessary distributed-system overhead.

## Current status

The backend and the first complete browser workflow are implemented locally.
The current scope includes:

- Java 21 and Spring Boot 3.5.16.
- Secure-default HTTP configuration with no generated default user.
- Public health, status, and OpenAPI routes.
- PostgreSQL-first Flyway schema for workspaces, users, memberships, services, incidents, events, comments, and audit entries.
- Incident declaration, retrieval, status-transition, and timeline APIs.
- Transactional declaration/status events and corresponding audit records.
- Idempotent responder assignment/unassignment with eligible-member checks.
- Validated incident comments with role-separated create/read access.
- Workspace-scoped free-text search and status/severity/service filters.
- Stable, bounded pagination with a maximum page size of 100.
- Dashboard totals and complete status/severity count maps.
- Workspace-admin managed-service creation, bounded listing, and idempotent detail/lifecycle updates.
- Immutable, validated, workspace-unique service slugs and service audit entries.
- Native Logstash-format JSON console logs and correlated HTTP completion events.
- Validated/generated `X-Request-ID` propagation through response headers and MDC.
- Administrator-managed membership adds, role changes, removals, and member listing.
- Ordered row locks and an enabled-admin invariant that prevents workspace lockout.
- Transactional notification outbox entries for incident declaration/status changes.
- Bounded background claims, exponential retries, stale-claim recovery, and failed-job state.
- Administrator-only, workspace-scoped failed-job inspection with bounded pagination.
- Administrator-only failed-job replay that resets a retained failure to
  `PENDING` and writes an audit entry before the worker retries it.
- Explicit failure timestamps and a tenant/status/time index for operator queries.
- Database-backed login with BCrypt password verification.
- Public self-registration API that creates an enabled user with no automatic
  workspace membership.
- Configurable local failed-login throttling with generic authentication
  failures.
- Exact-origin CORS allowlist support that is empty by default.
- Signed access tokens, hashed rotating refresh tokens, and logout revocation.
- Enabled-account workspace membership checks for responder/admin mutations and viewer retrieval.
- Idempotent development seed data that requires an environment-supplied password.
- Validation and RFC 9457-style problem responses.
- H2-based unit and API tests in PostgreSQL compatibility mode.
- A real PostgreSQL Testcontainers migration test, pending Docker availability.
- A React 19 and TypeScript 6 browser client.
- Sign-in/session restoration and authenticated workspace discovery.
- Managed-service catalog listing, administrator create flow, lifecycle update
  control, and viewer read-only state.
- Dashboard, search, filters, pagination, declaration, detail, transition,
  assignment, comment, and timeline flows.
- Role-separated controls, responsive layouts, route focus, and explicit
  loading/empty/success/error states.
- Frontend unit, HTTP-boundary, route, and automated axe tests.

The event and audit tables are append-only through the current application repositories. Database-level immutability controls are not yet implemented.

The next quality gates are physical-keyboard/contrast/zoom/screen-reader
accessibility checks, Docker and PostgreSQL verification, CI execution,
PostgreSQL-backed or deployed OpenAPI inspection, and a deployment decision.

The GitHub Actions workflow and Dockerfile are present but have not run in
GitHub or a local Docker engine. CI is configured as independent dependency
review, backend verify/container, and frontend quality/build jobs. Dependabot
watches Maven, npm under `/frontend`, and GitHub Actions weekly.

Maven packaging uses a fixed archive output timestamp. Two consecutive local package runs produced byte-identical executable JARs.

Request IDs may contain letters, digits, `.`, `_`, or `-` and are limited to 64 characters. Missing or invalid values are replaced with a UUID. Completion logs include request ID, method, path, status, and duration; query strings, bodies, and authorization headers are not logged by this filter.

## Architecture

The backend is organized by feature:

- `configuration` - HTTP security and framework configuration.
- `system` - public operational status.
- `catalog` - managed-service identity and workspace ownership.
- `incident` - incident aggregate, workflow, timeline, persistence, application service, and HTTP API.
- `audit` - append-only application writes for material incident changes.
- `common` - consistent API errors.

Feature packages communicate through application services and identifiers. ServicePulse is not presented as a microservices system.

See:

- `docs/PRODUCT_SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/DEMO_GUIDE.md`
- `docs/adr/0001-modular-monolith.md`
- `docs/INTERVIEW_GUIDE.md`
- `docs/LEARNING_PATH.md`
- `CHANGELOG.md`
- `SECURITY.md`
- `frontend/README.md`

## Requirements

- Java 21.
- Maven Wrapper (included; it downloads Maven 3.9.16).
- Node.js 22.12 or newer and npm for the browser client.
- Docker only for PostgreSQL integration tests and the local Compose environment.

## Verification without Docker

PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

The unit/API test profile uses H2 in PostgreSQL mode. This is a development substitute, not proof that PostgreSQL integration passed.

## PostgreSQL integration verification

With Docker available:

```powershell
.\mvnw.cmd verify
```

Failsafe runs `*IT` tests, including a PostgreSQL 17.7 Testcontainers migration/context check.

## Local database and development user

Copy `.env.example` to `.env` for Docker Compose, choose local-only passwords, then:

```powershell
docker compose up -d postgres
```

Maven does not load `.env` automatically. Set the application variables in the current PowerShell session:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/servicepulse"
$env:DATABASE_USERNAME = "servicepulse"
$env:DATABASE_PASSWORD = "<your local password>"
$env:JWT_SECRET_BASE64 = "<at least 32 random bytes encoded as Base64>"
$env:SERVICEPULSE_DEV_ADMIN_PASSWORD = "<a local-only password of 12 or more characters>"
```

Optional login-throttle overrides:

```powershell
$env:LOGIN_THROTTLE_ENABLED = "true"
$env:LOGIN_THROTTLE_MAX_FAILURES = "5"
$env:LOGIN_THROTTLE_FAILURE_WINDOW = "PT10M"
$env:LOGIN_THROTTLE_LOCKOUT = "PT15M"
```

Optional CORS allowlist for a deliberately separate frontend origin:

```powershell
$env:SERVICEPULSE_CORS_ALLOWED_ORIGINS = "https://portfolio.example.com,http://localhost:5173"
```

Allowed origins must be explicit HTTPS origins or local development origins.
Wildcard origins and trailing slashes are rejected at startup.

Then run:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

No `.env` file should be committed.

## Full local Compose topology

After creating the local-only `.env` values, the intended full stack is:

```powershell
docker compose up --build
```

- Web client: `http://localhost:4173`
- Direct API/OpenAPI access: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

The web container serves the immutable Vite assets, sends client routes to
`index.html`, and proxies only `/api/` plus `/actuator/health` to the backend.
The preferred browser topology is therefore same-origin. Separate frontend
origins require an explicit `SERVICEPULSE_CORS_ALLOWED_ORIGINS` allowlist and a
fresh token-transport review before public deployment. Compose waits for
PostgreSQL health before starting Spring and for Spring health before starting
the web tier.

Inspect and stop the local topology with:

```powershell
docker compose ps
docker compose logs backend frontend
docker compose down
```

`docker compose down -v` also deletes the local PostgreSQL volume and should be
used only when an intentional database reset is wanted.

Docker is unavailable in the current workstation, so these image and Compose
commands are documented but have not been executed.

In another PowerShell session, start the browser client:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Vite proxies `/api` and `/actuator` to the local API on port 8080. See
`frontend/README.md` for verification commands and the current session-storage
security trade-off.

## Frontend verification

```powershell
cd frontend
npm.cmd run check
npm.cmd audit --audit-level=high
```

The current local gate passes 20 tests, lint, TypeScript, the production build,
and a high-severity npm audit with zero reported vulnerabilities. The frontend
uses a small app-local browser router to avoid the current vulnerable React
Router dependency path. A real browser run against the API and H2 development
substitute verified the primary incident workflow at desktop and 390 px mobile
widths. This does not replace the blocked PostgreSQL, container, CI, or
deployment gates.

## CI configuration

The frontend CI job uses Node 24, restores the exact lockfile with `npm ci`,
runs `npm run check`, performs a high-severity npm audit, and uploads the
production bundle. It also builds the unprivileged web image. The backend job
runs Maven `verify`, including the
PostgreSQL Testcontainers gate, then builds the non-root container image. Pull
requests also receive GitHub's dependency review.

These jobs are configuration only until the repository is pushed and GitHub
Actions records a run.

## Public routes

- `GET /actuator/health`
- `GET /api/v1/system/status`
- `GET /v3/api-docs`
- `/swagger-ui/index.html`

Business routes require a signed access token. Development seed data is created only under the `dev` profile and only when an environment-supplied password is present. No default application credential exists.

The generated OpenAPI 3.1 document identifies the ServicePulse API and build
version, defines an HTTP bearer/JWT scheme, and applies it globally.
Registration, login, refresh, and public system status explicitly override
that requirement with an empty security array. A runtime H2-substitute check
observed all 20 implemented
paths; PostgreSQL and deployed OpenAPI checks remain separate gates.

Authentication routes:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `GET /api/v1/workspaces`

Registration stores a BCrypt password hash, normalizes the email address to
lowercase, writes a user audit entry, and deliberately does not grant workspace
membership. An administrator must add the enabled user to a workspace before
the user can view or mutate workspace data.

Access tokens are short-lived HMAC-signed JWTs. Refresh tokens are opaque random values; only SHA-256 hashes are stored, and a successful refresh rotates and revokes the prior token.

Failed login attempts are counted by normalized email address in a local
in-memory limiter. By default, five failures in a ten-minute window block
additional login attempts for fifteen minutes while returning the same generic
invalid-credentials response. The limiter is intended for local demonstration
and single-instance deployments; distributed/shared-store throttling and edge
rate limits remain future work.

The workspace directory returns only the authenticated enabled user's current memberships, including role, so clients can select a real tenant boundary after login.

Incident routes:

- `POST /api/v1/incidents`
- `GET /api/v1/incidents?workspaceId=...`
- `GET /api/v1/incidents/{id}`
- `POST /api/v1/incidents/{id}/transitions`
- `GET /api/v1/incidents/{id}/events`
- `PUT /api/v1/incidents/{id}/assignee`
- `DELETE /api/v1/incidents/{id}/assignee`
- `POST /api/v1/incidents/{id}/comments`
- `GET /api/v1/incidents/{id}/comments`
- `GET /api/v1/dashboard/summary?workspaceId=...`

Administrators and responders may declare, transition, assign, and comment on incidents. Viewers may retrieve incident state, comments, and its event timeline. Disabled accounts are excluded from current membership checks.

The incident list accepts optional `query`, `serviceId`, `status`, and `severity` filters plus zero-based `page` and bounded `size` parameters. Results are ordered by declaration time and ID. Free-text wildcard characters are escaped before database matching.

Managed-service routes:

- `POST /api/v1/workspaces/{workspaceId}/services`
- `GET /api/v1/workspaces/{workspaceId}/services?page=0&size=20`
- `GET /api/v1/workspaces/{workspaceId}/services/{serviceId}`
- `PUT /api/v1/workspaces/{workspaceId}/services/{serviceId}`

Only administrators may create or update services; responders and viewers may read them. The service collection returns a page envelope with `items`, `page`, `size`, `totalElements`, and `totalPages`, uses stable name/ID ordering, and caps `size` at 100. Service deletion is deliberately absent while incidents can reference service history.

Membership routes:

- `GET /api/v1/workspaces/{workspaceId}/members`
- `POST /api/v1/workspaces/{workspaceId}/members`
- `PUT /api/v1/workspaces/{workspaceId}/members/{userId}`
- `DELETE /api/v1/workspaces/{workspaceId}/members/{userId}`

Members may list their workspace directory; only administrators may add, change,
or remove memberships. Adds reference existing enabled users, including users
created through self-registration. Invitations are not implemented. A workspace
must retain at least one enabled administrator.

Notification operations route:

- `GET /api/v1/workspaces/{workspaceId}/notification-jobs/failed`
- `POST /api/v1/workspaces/{workspaceId}/notification-jobs/{jobId}/replay`

Only current workspace administrators may inspect failed jobs. The response is
bounded to 100 records per page, ordered by failure time and ID, and includes
only job/incident IDs, event type, attempt count, the sanitized exception class,
and created/failure timestamps. It does not expose incident summaries, comments,
credentials, or uncontrolled exception messages.

Administrators may replay a retained failed notification job. Replay is a
state reset, not direct external delivery: it clears failure fields, resets the
attempt count to zero, schedules the same job ID for immediate worker retry,
and writes a `NOTIFICATION_JOB_REPLAYED` audit entry. Non-failed jobs return a
conflict response; jobs outside the workspace remain inaccessible.

## Background notifications

Incident declarations and status changes enqueue notification jobs in the same database transaction. A scheduled worker claims a bounded batch, passes the job UUID to the delivery adapter as a stable idempotency key, retries failures with capped exponential backoff, reclaims stale processing jobs, and moves exhausted jobs to `FAILED`.

The included adapter writes a structured local log only. No email, chat, webhook, or cloud notification integration has been executed. Delivery semantics are at least once: an adapter must deduplicate by notification ID if a process stops after delivery but before the database acknowledgement.

Failed jobs are retained with a `failed_at` timestamp and can be inspected or
replayed through administrator endpoints. Replay keeps the same job UUID as
the delivery idempotency key and returns the job to `PENDING` for the scheduled
worker; it does not call an external provider inside the operator request.

Worker settings are configurable with `NOTIFICATION_BATCH_SIZE`, `NOTIFICATION_MAX_ATTEMPTS`, `NOTIFICATION_RETRY_BASE`, `NOTIFICATION_CLAIM_TIMEOUT`, `NOTIFICATION_POLL_DELAY_MS`, and `NOTIFICATION_INITIAL_DELAY_MS`.

## License

MIT
