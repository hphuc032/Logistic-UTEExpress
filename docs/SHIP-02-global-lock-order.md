# SHIP-02: multi-role Order/Account lock ordering

## Confirmed inversion and correction

VendorOrderAuthority previously locked the active vendor Account before locking Order. Shipping locks Order, Shipment and then the assigned Account (FOR SHARE). An account with both VENDOR and SHIPPER could therefore hold Account while waiting for an Order held by fulfillment, which in turn waited for that Account.

The correction moves the existing vendor eligibility lock after the owned Order has been locked and refreshed. The non-locking approved-shop lookup scopes the Order query; the existing authorization and final locked Shop validation remain in place. Vendor eligibility is still checked and locked through transaction completion. No account/role lock is removed, no role exclusivity is assumed, and Shipping's TOCTOU protection is preserved.

For mutations of an existing Order, Order is the first common serialization resource. Shipping then locks its Shipment and assigned Account; Vendor holds Order before its Account and subsequent cancellation effects. Shipment locks are scoped to that already-held Order, so competing Vendor/Shipping mutations cannot hold those resources in the reverse Order/Account cycle. Checkout creates a new Order after buyer/cart/inventory locks; its idempotency lookup does not lock an existing Order. Governance account/role operations do not acquire Order locks. Future Ops cancellation must follow the same resource-first rule and must not acquire Account before waiting for an existing Order.

This targeted change is in TD's Order-owned VendorOrderAuthority because the inverse edge originates there. HP/TD should review the ownership-boundary change together with QD's Shipping integration. It does not implement the proposed Ops cancellation API, choose new reason codes, or decide restricted-shop policy.

## PostgreSQL regression

ShipperFulfillmentIT gives the same persisted account both VENDOR and SHIPPER roles, makes it the shop owner and assigned shipper, and holds Order in the outer fulfillment transaction. A competing vendor worker requests cancellation through the real Order lifecycle boundary. PostgreSQL pg_blocking_pids proves that worker is waiting for Order before fulfillment proceeds to eligibility and pickup. The stale cancellation must conflict after pickup commits. Both transactions must finish within bounded time; Order and Shipment must be PICKED_UP and COD remain UNPAID.

The test keeps eligibility locking intact. Existing ORD-05 tests continue to cover Admin restriction/role revocation, rollback and concurrent delivery. This regression targets the reported Order/Account inversion; it is not a proof that all possible future lock graphs are deadlock-free.

The pre-fix version of the regression was executed against the original Vendor authority and PostgreSQL reported deadlock detected. The corrected version passed; the final regression also exercises lifecycle cancellation rather than only its locking authority.

VendorOrderIT's four concurrent mutation cases now hold Order instead of Account and observe workers waiting for Order. Their original single-winner, version, stock, history and payment assertions are retained. The 73 Vendor cases and six coordinator cases passed the focused PostgreSQL gate.

## Integrated TD regression evidence (2026-10-12)

The final ShipperOrderLifecycleIT regression suite is imported from TD's verification commit 3b442c0 on verify/ship-02-multirole-locks. The original deadlock characterization remains available on feature/ord-ops-01-cancellation at d100cab. Those branches/reports retain their own historical test results; importing the tests does not claim a fresh result for this branch.

The imported suite adds ten invocations: Vendor/pickup and Vendor/ADMIN reassignment in both scheduling directions, retained-lock pickup schedules, ADMIN+SHIPPER reassignment on a different Order, ADMIN-role revocation during fulfillment, and Vendor eligibility versus Account restriction. It asserts real PostgreSQL blockers, final versions, stock, assignment/history/audit effects and unchanged Payment facts. SQL exceptions including deadlock and lock/statement timeout fail the worker; no expected-deadlock characterization is retained in the executable regression suite.

Supported existing-Order entry points must begin without an earlier conflicting Account lock. REQUIRED/MANDATORY boundaries cannot reorder locks acquired by an outer caller. No current production lifecycle caller with Account-before-existing-Order composition was found in TD's audit or QD's inspected controller/coordinator paths. This is a documented precondition and residual risk, not proof that arbitrary joined compositions are safe. HP/TD should review earlier managed Account snapshots separately; this integration does not change Identity locking or freshness policy.

Future Ops cancellation must use the agreed resource-first hierarchy, retain persisted eligibility checks through commit, and preserve the existing Order lifecycle authority. cancelForOps, reason enum and restricted-shop policy remain pending; these regression tests do not implement or authorize that proposed operation.

Fresh integration verification on 2026-10-12: Java 21, .\mvnw.cmd -q -Ppostgres-it clean verify, exit 0; 1,106 tests, zero failures/errors/skips. ShipperOrderLifecycleIT ran 48 invocations. This is QD's full repository run after importing the final TD regression file, distinct from TD's focused 152-test run.
