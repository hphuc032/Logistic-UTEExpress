# USER-02 Address Management

## Scope

USER-02 adds authenticated address management for `USER` and `VENDOR` accounts. It does not implement checkout, shipping selection, or order address snapshots.

## Persistence

Migration `V20260927123343__hp_user02_addresses.sql` creates `uteexpress.addresses` with a required user foreign key, receiver and delivery fields, UTC technical timestamps, optimistic `version`, named nonblank checks, and no cascade delete. A partial unique index on `user_id` where `is_default = true` prevents more than one default address per account.

`Address` stores `userId` instead of a `UserEntity` association. Browser requests never accept an owner id. The service resolves the owner through `CurrentAccountIdProvider`, and every lookup or mutation is scoped by both address id and user id.

## Default-address invariant and concurrency

- An account with no addresses has no default.
- Its first address becomes default automatically.
- Later addresses are non-default until explicitly selected.
- Updating an address preserves its owner and default flag.
- Deleting a non-default address does not change the default.
- Deleting the default promotes the remaining address with the smallest id.
- Deleting the last address returns the account to zero addresses and zero defaults.

Mutations lock the owning `users` row through `AccountIdentityService.requireActiveAccountForUpdate`. This serializes first creation, default changes, and deletion for one account. The database partial unique index remains the final guard against two defaults. PostgreSQL tests exercise concurrent first creation and concurrent default selection.

## Web and validation

The shared Thymeleaf page is `templates/account/addresses.html`. Routes are:

- `GET /user/addresses`
- `POST /user/addresses`
- `POST /user/addresses/{id}/update`
- `POST /user/addresses/{id}/delete`
- `POST /user/addresses/{id}/default`

All unsafe requests remain CSRF protected. The controller allowlists `receiverName`, `phone`, `provinceCode`, `district`, and `detail`, so request parameters cannot assign `id`, `userId`, `isDefault`, timestamps, or version. Form values are trimmed and validated for required content, database-aligned length limits, control characters, and a permissive international phone format. Thymeleaf output uses escaped text.

## Checkout boundary

`AddressQueryService` exposes immutable `AddressData` snapshots through `listOwnedAddresses`, `requireOwnedAddress`, and `findDefaultAddress`. A future checkout service must supply the authenticated account id through its trusted service boundary and must use `requireOwnedAddress` before accepting an address. Checkout does not access `AddressRepository` or `Address` directly.

## Verification

- Unit tests cover lifecycle, ownership, deterministic replacement, and the default invariant.
- MVC tests cover authorization, CSRF, validation, mass-assignment resistance, safe cross-user behavior, empty/default UI states, and escaped output.
- PostgreSQL Testcontainers tests cover Flyway, Hibernate validation, field constraints, foreign keys, the partial unique index, real JWT ownership, concurrency, and the checkout read contract.
- Existing architecture, authentication, profile, cart, catalog, shop, shipping, and UI tests remain in the quality gate.

The actual final Maven and CI results are recorded in the pull request and task completion report after execution.

## Deferred

- Checkout address selection and snapshotting: CHK-01
- Shipping calculations and delivery workflow
- Order creation and immutable order address data
