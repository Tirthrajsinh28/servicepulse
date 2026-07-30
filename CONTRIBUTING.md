# Contributing to ServicePulse

ServicePulse is an independent portfolio project for demonstrating a
backend-focused incident-management platform. Contributions should improve the
actual system, its tests, or its documentation without overstating the current
verification state.

## Ground rules

- Use synthetic data only.
- Do not copy or infer employer source code, internal workflows, customer data,
  credentials, or private architecture.
- Do not commit `.env` files, tokens, private keys, database dumps, logs with
  secrets, screenshots containing secrets, or generated build outputs.
- Keep ServicePulse described as an independent portfolio project, not an
  employment project.
- Do not claim PostgreSQL container, Docker Compose, remote CI, or deployment
  evidence until those checks have run and are recorded.

## Backend verification

Run from `projects/servicepulse`:

```powershell
.\mvnw.cmd clean test
```

This gate covers the non-Docker Spring Boot regression suite and the enforced
JaCoCo line-coverage minimum. Docker-backed Testcontainers verification remains
blocked until Docker Desktop is available.

## Frontend verification

Run from `projects/servicepulse/frontend`:

```powershell
npm.cmd ci
npm.cmd run check
npm.cmd audit --audit-level=high
```

Use `npm.cmd` on Windows because PowerShell may block the `npm.ps1` shim.

## Security and privacy expectations

- Preserve authentication and workspace-authorization tests when touching API
  boundaries.
- Keep validation failures and authorization failures structured and
  non-sensitive.
- Do not expose refresh tokens, signing keys, seed passwords, or raw audit data
  in logs or UI copy.
- Keep external notification delivery clearly labeled as a local logging
  adapter until a real provider is designed and verified.
- Update `SECURITY.md` when supported versions, reporting paths, or limitations
  change.

## Documentation expectations

Update the relevant files when behavior changes:

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/INTERVIEW_GUIDE.md`
- `docs/LEARNING_PATH.md`
- `CHANGELOG.md`
- OpenAPI-related tests or documentation

Separate completed, verified behavior from future work and blocked checks.

## Pull request notes

Include:

- Changed backend/frontend behavior.
- Database migration impact, if any.
- Commands run and results.
- Known limitations.
- Whether Docker, PostgreSQL, remote CI, or deployment checks were executed.
