# Changelog

## [0.1.0] - 2026-07-30

- Build the ServicePulse modular-monolith incident-management slice with
  workspace-scoped services, incidents, timelines, comments, assignments,
  audit records, search, pagination, dashboard summaries, health checks,
  structured logs, metrics, and OpenAPI documentation.
- Add database-backed authentication, short-lived access tokens, rotating
  refresh tokens, logout revocation, enabled-account membership checks, and
  role-based authorization.
- Add a React/TypeScript browser client for sign-in, workspace discovery,
  dashboard, incident declaration, incident detail, transitions, assignment,
  comments, timeline, role-separated views, and custom 404.
- Add transactional notification outbox, bounded worker behavior, retries,
  stale-claim recovery, failed-job state, and administrator-only failed-job
  inspection.
- Add local verification evidence with H2 PostgreSQL-mode tests, frontend
  checks, H2-substitute runtime/browser flow, OpenAPI inspection, Docker/CI
  configuration, Dependabot, pull-request template, and issue template.
- Add public GitHub Actions verification for frontend and backend jobs,
  including PostgreSQL integration tests and frontend/backend container image
  builds.
- Add labeled frontend screenshot demo mode and current synthetic screenshots
  for the public portfolio source.

Local Docker Compose runtime checks, external deployment, registration/
invitations, and external notification delivery remain pending or intentionally
deferred.
