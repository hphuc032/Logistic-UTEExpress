# DB-01 Seed manifest

Status: planned data, not an executable full-demo seed. DB-01 has no business
tables. Running its baseline seeds no account, role, product or order.
Owners add seed fixtures alongside their own migrations and tests.

## Execution contract

Demo seeding must require an explicit `demo` profile plus opt-in by the person
running it. Keep the implementation separate from `db/migration` so production
schema migration never creates demo accounts. No demo runner is installed by
DB-01. Do not add a demo profile that silently pretends the seed has run.

Use a manifest identifier and stable natural keys (email, role code, shop slug,
product fixture key, order code). Resolve generated IDs after insertion; do not
assume ID 1 or reset sequences. Use transactional inserts/upserts and tests for
two consecutive executions. On conflict preserve live progress and user edits;
never reset stock, passwords, payment state or delivery history on rerun.

Passwords come from a local environment input and are hashed through the
identity service; do not commit plaintext or reusable password hashes. Use
fictional data, no real customer information. No secrets, OTPs, JWTs or sensitive
request payloads in logs/audit. Never seed production credentials.

## Ordered manifest

| Stage | Seed data | Owner / prerequisite |
| --- | --- | --- |
| 1 | USER, VENDOR, MANAGER, ADMIN, SHIPPER role codes; no GUEST row | HP / AUTH-01; exact security contract |
| 2 | Fictional activated demo users for each actor; buyer A/B and shop owner A/B for ownership tests | HP / AUTH-01 + AUTH-02; explicit credentials |
| 3 | Buyer addresses, approved/pending/rejected shops | HP with QD approval flow / USER-02, VENDOR-01, ADMIN-03 |
| 4 | Categories; active/inactive shipping providers and region/service fees | QD / category task and SHIP-00 |
| 5 | Commission policy effective at checkout | QD / policy schema and reviewed TD pricing contract |
| 6 | At least 12 public products across shops/categories, images, out-of-stock/hidden cases | HP / PROD-00 then PROD-01 |
| 7 | Multi-shop carts, selected/unselected items and fixture promotions/vouchers | TD / CART and PROMO; honor availability |
| 8 | Orders/items snapshots, payments and status history for core and exception flows | TD / lifecycle service, PAY-01; never update order status directly |
| 9 | Assigned/unassigned shipments, success/failure cases and matching order history | QD via TD lifecycle contract / SHIP tasks |
| 10 | Return/refund fixtures consistent with payment and order | TD / ORD-04 + PAY-02 |
| 11 | Favorites/views, eligible reviews/media, recipient notifications and sanitized audit records | QD / corresponding modules |

Stages describe dependencies, not instructions to implement future tasks now.
Reference tables needed by earlier migrations (notably commission policies)
must exist before their consumers even if their UI is scheduled later.

## Acceptance for the later seed implementation

- Empty database migrates and seeds in the documented order without FK errors.
- Rerun produces no duplicate natural keys or new payment/delivery side effects.
- Ownership fixtures genuinely use different users and shops.
- Invalid FK, duplicate and CHECK fixtures are tested separately and rolled back.
- Product snapshots, payment totals and shipment/order states stay consistent.
- Production startup executes no demo seed; no fixed credentials in the repo.
- Report counts and fixture identifiers, never passwords or tokens.
