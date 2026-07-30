# ADR 0002: Secure the HTTP Boundary Before Authentication Exists

- Status: Accepted
- Date: 2026-07-01

## Context

Spring Security can generate a development password when no user service exists. Publishing or relying on an implicit credential would make the foundation misleading and unsafe.

## Decision

Disable generated user auto-configuration. Permit only health, operational status, API documentation, login, and refresh. Require authentication for every other route and apply membership-aware method security.

## Consequences

- Business APIs are usable only with signed access tokens.
- Mock users and real token tests verify authorization contracts.
- There is no default application credential to leak or mistake for production configuration.
