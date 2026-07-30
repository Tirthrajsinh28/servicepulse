# ADR 0003: Separate Short-Lived Access Tokens from Rotating Refresh Tokens

- Status: Accepted
- Date: 2026-07-01

## Context

ServicePulse needs stateless API authentication without embedding workspace roles that may become stale. Long-lived bearer JWTs would increase replay risk, while storing raw refresh tokens would turn a database leak into reusable credentials.

## Decision

- Verify enabled users with BCrypt password hashes.
- Issue short-lived HMAC-signed JWT access tokens containing user identity and email.
- Resolve workspace roles from the database for each protected workspace operation.
- Issue 256-bit opaque refresh tokens.
- Store only SHA-256 refresh-token hashes.
- Rotate and revoke the previous refresh token on every successful refresh.
- Revoke a refresh token on logout.
- Require a Base64 signing secret of at least 32 decoded bytes with no default value.
- Perform a dummy BCrypt verification for unknown emails to reduce login timing differences.

## Consequences

- Workspace role changes take effect immediately.
- API requests require a database authorization check for workspace operations.
- Refresh reuse is rejected after rotation.
- Signing-key rotation, refresh-family compromise handling, distributed/shared-store throttling, and authentication audit events remain future work.
- Logout revokes refresh capability; already-issued access tokens remain valid until their short expiry.
