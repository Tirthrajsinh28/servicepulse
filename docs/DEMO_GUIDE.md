# ServicePulse demo guide

This guide helps the candidate demonstrate ServicePulse as an independent
portfolio project. Use synthetic data only, and do not describe the project as
an employment system, deployed service, or PostgreSQL-verified runtime until
those checks have actually run and are recorded.

## What to prove in a short demo

- A user signs in with a local development seed account.
- The UI discovers workspaces from the authenticated API instead of hard-coding
  a tenant.
- The dashboard shows incident totals and status/severity summaries.
- A responder/admin can declare an incident, transition status, assign a
  responder, add a comment, and inspect the timeline.
- A viewer can read incident state without mutation controls.
- The API exposes health and OpenAPI documentation.
- Local tests cover authentication, authorization, workflow, timeline, search,
  dashboard, accessibility, and OpenAPI boundaries.

## Pre-demo verification

Run the local checks before recording or presenting the project:

```powershell
.\mvnw.cmd test
Set-Location frontend
npm.cmd run check
npm.cmd audit --audit-level=high
```

If Docker is available, run the PostgreSQL-backed gate separately:

```powershell
.\mvnw.cmd verify
docker compose up --build
```

If Docker is not available, say clearly that PostgreSQL/Testcontainers and
Compose execution remain pending. The H2 development substitute is useful for
local checks, but it is not PostgreSQL evidence.

## Local browser flow

1. Start the API using the `dev` profile with a local-only development seed
   password and a non-default JWT signing secret. Do not commit those values.
2. Start the frontend from `projects/servicepulse/frontend`:

   ```powershell
   npm.cmd install
   npm.cmd run dev
   ```

3. Open the Vite URL shown by the terminal.
4. Sign in with the configured development seed email. The default seed email
   is `dev-admin@servicepulse.local` unless overridden by environment.
5. Select the discovered workspace.
6. Show the dashboard cards and status/severity breakdowns.
7. Open the incidents list, search/filter, and explain bounded pagination.
8. Declare a synthetic incident for the seeded service.
9. Transition the incident through the workflow.
10. Assign a responder/admin and confirm the timeline shows a friendly member
    label rather than a raw UUID.
11. Add a short synthetic comment.
12. Open the timeline and explain the difference between incident events and
    audit records.
13. Show the failed-notification inspection and replay API tests if discussing
    operator recovery.
14. Show OpenAPI at `/v3/api-docs` or Swagger UI when the API is running.

## Suggested two-minute narration

“ServicePulse is an independent incident-management platform for fictional
development teams. I built it as a Spring Boot modular monolith with
workspace-scoped authorization, incident workflow, audit history, a
transactional notification outbox, and a React/TypeScript client. The demo
starts with database-backed login, then shows tenant-aware dashboard data,
incident declaration, transitions, assignment, comments, and timeline history.
The local verification includes backend tests, frontend route/accessibility
tests, OpenAPI checks, and a browser flow against a labeled local substitute.
Docker/PostgreSQL and remote CI are configured but still need an environment
where they can run.”

## Evidence to show during the demo

- `README.md` current status and limitations.
- `docs/ARCHITECTURE.md` package boundaries and trade-offs.
- `docs/INTERVIEW_GUIDE.md` for deeper explanation.
- `reports/TEST_REPORT.md` in the parent workspace for the latest recorded
  verification results.

## Do not claim

- Public deployment, remote CI, or container execution unless those checks have
  run and are recorded.
- PostgreSQL integration from H2-only evidence.
- External notification delivery; the current adapter logs locally.
- Security completeness, defect-free behavior, or production traffic.
