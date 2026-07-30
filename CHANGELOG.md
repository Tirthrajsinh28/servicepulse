# Changelog

## [Unreleased]

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

PostgreSQL/Testcontainers execution, Docker Compose runtime checks, remote
GitHub Actions, deployment, screenshots, and release tag remain pending.
