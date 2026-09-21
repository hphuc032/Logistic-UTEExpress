# SEC-01 Security foundation

SEC-01 establishes authorization contracts and safe defaults. It does not authenticate
real accounts. AUTH-01 owns account persistence and AUTH-02 owns JWT login and logout.

## Roles

`RoleCode` is the single role contract: `USER`, `VENDOR`, `MANAGER`, `ADMIN`, and
`SHIPPER`. Its `authority()` method produces the Spring Security names `ROLE_USER`,
`ROLE_VENDOR`, `ROLE_MANAGER`, `ROLE_ADMIN`, and `ROLE_SHIPPER`. Business code must
not concatenate the `ROLE_` prefix. Guest is an unauthenticated request, not a role.

## Route policy

Anonymous access is limited to:

- `/`, `/products/**`, `/categories/**`, `/shops/**`
- `/login`, `/register`, `/verify-otp`, `/forgot-password`, `/reset-password`
- `/api/v1/foundation`, `/actuator/health`

Future route conventions are:

| Route | Required authority |
| --- | --- |
| `/user/**` | `ROLE_USER` or `ROLE_VENDOR` |
| `/vendor/**` | `ROLE_VENDOR` |
| `/manager/**` | `ROLE_MANAGER` |
| `/admin/**` | `ROLE_ADMIN` |
| `/shipper/**` | `ROLE_SHIPPER` |

All remaining requests require authentication. A public route that has no controller
still returns 404. The route list does not create endpoints or grant business ownership.

## Current user and ownership

Business services obtain the current subject through `CurrentUserProvider`, which reads
the Spring `SecurityContext` and returns an immutable `CurrentUser` snapshot containing
the subject, roles, and authorities. AUTH-02 supplies `UteExpressPrincipal` with the
stable subject `uteexpress:user:<id>`. Services that require the persistent account ID
use `CurrentAccountIdProvider`; they must not parse the JWT or authentication name.

Role checks do not replace ownership checks. Future services must scope repository
queries or validate ownership, such as buyer, shop owner, or assigned shipper, using the
trusted current user. IDs in request bodies, query parameters, or hidden form fields must
never decide ownership.

## Method security and CSRF

`@EnableMethodSecurity` enables service-level `@PreAuthorize` rules. URL authorization
is the first gate; business services remain responsible for role, ownership, and state.

CSRF remains enabled with `CookieCsrfTokenRepository`. The readable `XSRF-TOKEN` cookie
is separate from the HttpOnly authentication cookie. Unsafe browser requests require a
matching CSRF token, including login, registration, and logout. Thymeleaf forms carry a
hidden token; browser clients may send the corresponding header. The security context
is stateless and is never persisted as authenticated session state.

## Passwords and errors

The shared `PasswordEncoder` is `BCryptPasswordEncoder`. AUTH-01 will use the bean;
SEC-01 does not store passwords or create accounts.

AUTH-01 now persists identities in `users`, `roles`, and `user_roles`. Public
registration normalizes email and username, hashes the password with the shared BCrypt
encoder, assigns only the seeded `USER` role, and creates the account as
`PENDING_VERIFICATION` with `email_verified_at` unset and `token_version` equal to zero.
Database unique constraints remain the final authority for normalized identity conflicts.

Unauthenticated protected requests return HTTP 401 with `UNAUTHENTICATED`. Authenticated
requests without permission and rejected CSRF requests return HTTP 403 with
`ACCESS_DENIED`. Filter-chain handlers and MVC method-security handling use the ARCH-01
`ErrorResponse` shape and do not expose exception messages or stacktraces. HTML error
pages can be added by UI-01 without weakening the API contract.

## Authentication boundary

Spring Boot's generated development user remains disabled. The application-owned
`UteExpressUserDetailsService` loads an internal `AuthAccountSnapshot` through
`IdentityAuthenticationService`; the security module does not access identity entities
or repositories. Login accepts normalized username or email, uses the shared BCrypt
encoder through `DaoAuthenticationProvider`, and permits only `ACTIVE` accounts. Public
authentication failure messages do not reveal whether an account exists or its status.

JWT access tokens use Spring Security JOSE with HS256. The signing key comes only from
`JWT_SECRET_BASE64`, must decode to at least 32 bytes, and is never logged. Tokens expire
after 30 minutes and contain only `sub`, `iat`, `exp`, `iss`, `aud`, and `tokenVersion`.
Issuer, audience, timestamps, subject format, token version, and signature are validated.
Roles are deliberately absent from the token.

The `UTEEXPRESS_AUTH` cookie is HttpOnly, `SameSite=Lax`, scoped to `/`, and Secure by
default. The local profile may disable Secure for localhost HTTP. Each authenticated
request reloads the account and roles from PostgreSQL, requires `ACTIVE`, compares the
database token version, maps roles through `RoleCode`, and fails closed for unknown roles.

`POST /logout` is CSRF protected. It atomically increments `users.token_version`, clears
the authentication cookie and security context, and therefore invalidates all JWTs
issued with an older version. There is no refresh token, token table, blacklist, session,
OAuth login, or production seed account.

AUTH-01 does not activate accounts or send email. AUTH-03 owns OTP creation, delivery,
verification, and the transition from `PENDING_VERIFICATION` to `ACTIVE`.
