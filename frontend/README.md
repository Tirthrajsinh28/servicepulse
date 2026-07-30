# ServicePulse frontend

This React and TypeScript client is the browser interface for the ServicePulse
incident-management API. It uses real workspace memberships returned after
authentication; no tenant ID or product data is hard-coded into the production
client.

## Implemented flows

- Development-account sign-in, session restoration, refresh rotation, and sign-out.
- Authorized workspace discovery and selection.
- Dashboard totals plus incident search, filters, and bounded pagination.
- Incident declaration with service and severity selection.
- Incident detail, workflow transitions, assignment, comments, and append-only history.
- Administrator/responder mutation controls and a read-only viewer experience.
- Loading, empty, success, error, validation, unauthorized, and not-found states.
- Responsive layout, skip navigation, route focus, and reduced-motion support.

## Requirements and commands

Node.js 22.12 or newer is required.

```powershell
npm.cmd install
npm.cmd run check
npm.cmd run dev
```

`npm run check` runs ESLint, the TypeScript build-mode check, Vitest, and the
production Vite build. The current test suite uses an in-memory API adapter for
role-separated user flows and a mocked `fetch` boundary for token refresh,
request headers, and problem-detail mapping.

CI performs `npm ci` before this gate so dependency restoration is locked to
`package-lock.json`. Dependabot is configured for weekly npm updates in this
directory.

The development server proxies `/api` and `/actuator` to
`http://127.0.0.1:8080`. Set `VITE_API_BASE_URL` when the API is hosted on
another origin. The backend denies cross-origin browser calls unless
`SERVICEPULSE_CORS_ALLOWED_ORIGINS` lists the exact frontend origin. Keep the
same-origin proxy topology unless a separate deployment target has been
intentionally reviewed.

## Screenshot demo mode

For screenshot capture only, the frontend can use a synthetic in-browser API
adapter:

```powershell
$env:VITE_SERVICEPULSE_DEMO_MODE = "true"
npm.cmd run dev -- --host 127.0.0.1 --port 5188
```

Demo mode shows a visible banner and uses fictional Northstar Labs data. It
does not represent a live backend, production data, cloud execution, employer
work, users, traffic, or deployment. Leave `VITE_SERVICEPULSE_DEMO_MODE`
unset for the normal API-backed client.

## Production container

The frontend Dockerfile performs a locked Node 24 build, then copies only the
bundle into the maintained unprivileged NGINX image. NGINX listens on 8080,
uses SPA fallback for client routes, gives fingerprinted assets an immutable
cache policy, keeps `index.html` uncached, and proxies `/api/` plus the health
endpoint to the Compose service named `backend`.

The proxy adds CSP, permissions, referrer, frame, and content-type response
headers. HSTS is intentionally absent because TLS termination has not been
selected; it belongs at the verified HTTPS edge.

The image has not been built locally because Docker is unavailable.

## Session model

The access token stays in JavaScript memory. The opaque refresh token and
selected workspace are stored in `sessionStorage`, which limits persistence to
the browser tab but does not make them immune to cross-site scripting. A public
deployment needs a deliberate CSP, dependency review, exact CORS allowlist, and
a fresh assessment of whether an `HttpOnly`, `Secure`, same-site cookie is the
better refresh-token transport.

No default credential is embedded in the client. The backend `dev` profile
creates a synthetic account only when the operator supplies
`SERVICEPULSE_DEV_ADMIN_PASSWORD`.

## Current evidence

- 20 Vitest tests pass across two files.
- Automated axe scans cover login, dashboard, service catalog administrator and
  viewer states, workspace membership administrator and viewer states, failed
  notification administrator and viewer states, declaration, editable incident
  detail, read-only viewer detail, and the custom 404 with no reported
  violations.
- ESLint and TypeScript checks pass.
- The Vite production build succeeds.
- `npm audit --audit-level=high` reported zero known vulnerabilities on
  2026-07-30.
- A local browser run against the Spring API and H2 development substitute
  verified login, dashboard, declaration, status transition, assignment,
  commenting, friendly audit labels, and a 390 px responsive view.
- A frontend demo-mode screenshot pass captured dashboard, incident-detail,
  and mobile service-catalog routes with a visible synthetic-data banner and
  verified no horizontal overflow at 390 px.
- Public ServicePulse CI run `30521691031` passed frontend and backend verify
  jobs at commit `7d947f7ccbb28978e7576663733b5e30edffcb4b`.

This evidence is not a deployment, production-data, user, traffic, or external
notification-delivery claim. PostgreSQL Testcontainers and local Compose remain
blocked until Docker is available in the local environment.
