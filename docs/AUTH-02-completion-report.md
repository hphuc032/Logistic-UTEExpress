# AUTH-02 login, JWT authentication, and logout

AUTH-02 adds database-backed login and stateless browser authentication without changing
the AUTH-01 schema. No account, password, JWT secret, token, or administrator credential
is seeded.

## Authentication flow

- `GET /login` renders the shared Thymeleaf layout.
- `POST /login` accepts only `identifier` and `password`, remains CSRF protected, and
  authenticates through Spring Security's `AuthenticationManager` and BCrypt provider.
- Identifier lookup trims and lowercases with `Locale.ROOT`, allowing username or email.
- Only `ACTIVE` accounts authenticate. Pending, locked, disabled, missing, and wrong-password
  cases return the same public failure message.
- `UteExpressPrincipal` uses `uteexpress:user:<id>` as its stable subject. Persistent IDs
  are exposed to trusted services through `CurrentAccountIdProvider`, never subject parsing.

## JWT and browser state

Spring Security JOSE issues HS256 JWTs with issuer `uteexpress`, audience `uteexpress-web`,
a 30-minute TTL, and at most 60 seconds of clock skew. Claims are limited to `sub`, `iat`,
`exp`, `iss`, `aud`, and `tokenVersion`. Roles, email, password data, and personal data are
not placed in the token.

The token is stored only in the `UTEEXPRESS_AUTH` cookie with HttpOnly, `SameSite=Lax`,
`Path=/`, and a Max-Age equal to the JWT TTL. Secure defaults to true; the local profile
uses false for localhost HTTP. `JWT_SECRET_BASE64` is mandatory and must decode to at
least 32 bytes.

Authentication is stateless. On every JWT request the server reloads the current account,
requires `ACTIVE`, compares `token_version`, reloads roles from PostgreSQL, and validates
each role through `RoleCode`. Role changes therefore apply on the next request without
trusting a JWT role claim.

## CSRF and logout

`CookieCsrfTokenRepository` supplies a separate readable CSRF cookie while JWT remains
HttpOnly. Login, logout, and all other unsafe methods require a valid CSRF token.

`POST /logout` increments `token_version` atomically, updates the optimistic version and
timestamp, clears the auth cookie and security context, and redirects to `/login?logout=true`.
This is logout-all-devices behavior. An invalid or expired auth cookie can still be cleared
after a valid CSRF submission.

## Verification

- `mvnw.cmd clean test`: 117 tests passed.
- `mvnw.cmd package`: passed and produced the executable Spring Boot JAR.
- `mvnw.cmd -Ppostgres-it verify`: 117 unit/MVC tests and 16 PostgreSQL 17.6
  integration tests passed through Docker Desktop/Testcontainers.
- Unit and MVC tests cover login, generic failure, ACTIVE-only access, JWT claims and
  validation, cookie attributes, filter behavior, stateless access, CSRF, route roles,
  logout, and application-owned `UserDetailsService`.
- PostgreSQL/Testcontainers tests cover username/email login, inactive states, live role
  reload, Admin authorization, account state changes, token-version invalidation, and old
  JWT rejection.
- Existing architecture, security, UI, registration, audit, database, and order tests remain
  part of the same Maven gates.

AUTH-03 still owns email OTP and activation. AUTH-04 owns password recovery. SEC-02 owns
rate limiting and later hardening. Refresh tokens are outside the current scope.
