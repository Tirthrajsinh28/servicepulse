# Security Policy

ServicePulse is an independent portfolio project using fictional organizations
and synthetic data. It demonstrates secure-by-default API and workflow
patterns, but it is not a production incident-management service.

## Supported scope

Security review currently applies to the local Java/Spring Boot API, React
client, database schema, authentication and authorization tests, Docker/CI
configuration, and documentation.

## Reporting

No public vulnerability-reporting email address is published yet because public
contact details still require candidate confirmation. Before public release,
enable GitHub private vulnerability reporting or repository security advisories.

Do not open public issues containing secrets, real incident details, customer
data, employer-confidential material, access tokens, `.env` values, or private
logs.

## Current controls

- Public routes are limited to health/status/API documentation and
  authentication endpoints.
- Authenticated routes enforce current enabled workspace membership and role
  boundaries in server-side tests.
- Access tokens are short-lived; refresh tokens are opaque, hashed, rotated,
  and revoked on logout.
- Failed login attempts are locally throttled by normalized email address while
  preserving the same generic invalid-credentials response.
- Cross-origin browser access is denied unless an explicit HTTPS or local
  development origin is configured.
- Validation errors use structured problem responses rather than stack traces.
- Logs omit request bodies, query strings, cookies, and authorization headers.
- Development seed credentials must be supplied through the environment.

## Current limitations

- PostgreSQL/Testcontainers, Docker Compose, container image behavior, and
  remote GitHub Actions remain unverified until Docker/GitHub publication are
  available.
- Registration/invitations, account recovery, distributed/shared-store rate
  limits, edge abuse protections, signing-key rotation, and broader abuse
  protections remain future work.
- The local H2 substitute is not PostgreSQL production evidence.
