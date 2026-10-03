# ADMIN-06 commission policy foundation for CHK-02

This change resolves the checkout dependency before the remaining ADMIN-06 manager workflow. The shared `CommissionQueryService.requireEffectivePolicy(checkoutAt)` now queries PostgreSQL; it never returns a fabricated default. If no policy exists, it fails with `CONFLICT`. The policy with the latest `effective_from <= checkoutAt` is selected. If that latest policy is inactive, checkout fails rather than reverting to an older rate. Policy timestamps are unique, so an ambiguous tie cannot be stored.

The forward migration adds `commission_policies` and the previously deferred `orders.commission_policy_id` FK. Existing Order rows with a null policy ID remain valid. CHK-02 must save the returned policy ID and rate snapshot and calculate the commission amount using the existing order contract; this change does not edit TD-owned Order/Checkout code.

Admin can create a dated policy via `GET/POST /admin/commissions`. Manager can list policies at `/manager/commissions`. Manager writes are denied until the team defines a concrete Manager rate limit; no arbitrary limit or seed rate has been invented. A policy must be created by an Admin before live checkout can succeed. Policy creation and audit logging share a transaction, and duplicate effective timestamps are rejected. Existing policy values are immutable so old Order snapshots remain stable.

Next ADMIN-06 increment after CHK-02 lands: agree Manager limits and policy stop/replace UX with TD, then test order snapshot integration. Review `rate_percent` precision (`NUMERIC(7,4)`) with TD before merging this foundation.
