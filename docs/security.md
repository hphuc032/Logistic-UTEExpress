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
the subject, roles, and authorities. SEC-01 uses the authenticated subject because no
User entity exists yet. AUTH-02 may supply a custom principal and stable account subject
without changing callers of this interface.

Role checks do not replace ownership checks. Future services must scope repository
queries or validate ownership, such as buyer, shop owner, or assigned shipper, using the
trusted current user. IDs in request bodies, query parameters, or hidden form fields must
never decide ownership.

## Method security and CSRF

`@EnableMethodSecurity` enables service-level `@PreAuthorize` rules. URL authorization
is the first gate; business services remain responsible for role, ownership, and state.

CSRF remains enabled with Spring Security defaults. Unsafe browser requests require a
valid CSRF token, including public form endpoints such as login and registration when
they are implemented. Thymeleaf forms will carry the token; AJAX clients must send the
corresponding header. Provider callbacks may only receive a narrow exception in their
own task after signature and replay validation are defined.

## Passwords and errors

The shared `PasswordEncoder` is `BCryptPasswordEncoder`. AUTH-01 will use the bean;
SEC-01 does not store passwords or create accounts.

Unauthenticated protected requests return HTTP 401 with `UNAUTHENTICATED`. Authenticated
requests without permission and rejected CSRF requests return HTTP 403 with
`ACCESS_DENIED`. Filter-chain handlers and MVC method-security handling use the ARCH-01
`ErrorResponse` shape and do not expose exception messages or stacktraces. HTML error
pages can be added by UI-01 without weakening the API contract.

## Authentication boundary

Spring Boot's generated development user is disabled. There is no production login,
in-memory user, fake token, or hardcoded credential in SEC-01. AUTH-02 must provide the
JWT authentication filter and current principal, token issue/signature/claims,
HttpOnly cookie, expiration, token-version validation, and logout invalidation. The
filter is added to the existing `SecurityFilterChain`; it must not disable CSRF globally.
