# Flyway migration reservations

QD coordination record, 2026-10-22. Baseline develop 425dd6aecf83c2f0b3e3147e1eda68e0b53368b0. Reservation is an agreed filename/version, NOT SQL execution, schema approval or a claim that another developer's unpublished branch was inspected.

| Task | Owner | Version / filename | State |
| --- | --- | --- | --- |
| RECEIVE_RETURN #53 | TD | V20261018090000__td_ord04_return_receipt.sql | Merged, unchanged |
| SHIP-03 #54 | QD | V20261018110000__qd_ship03_delivery_failures.sql | Merged, unchanged; current maximum on develop |
| PAY-02 | TD | **V20261022090000__td_pay02_refund_ledger.sql** | **RESERVED** for Payment refund ledger/constraints; no SQL created by QD |
| SHIP-04 | QD | **None** | No migration created/reserved in accessible QD worktrees or fetched remote refs |

Checked all 64 fetched remote refs for the PAY-02 candidate version; no collision found. No matching local 20261022/ship04 SQL was found in the accessible workspace. Unpublished work on other machines cannot be checked. TD must adopt this exact version and filename for the first PAY-02 ledger migration. QD does not add an empty placeholder migration.

Application order: Receipt 20261018090000 -> Shipping evidence 20261018110000 -> PAY-02 ledger 20261022090000. PAY-02 has no SHIP-04 schema dependency. If SHIP-04 later requires schema, QD must allocate a distinct version GREATER than 20261022090000 and the latest merged/reserved maximum at that time. If SHIP-04 is the Master Plan read-only dashboard, no migration is expected. No SHIP-04 timestamp is reserved merely because a wave table names the task.

Before applying or merging: fetch develop, check new maximum and other reservations. If another task overtakes this timestamp, coordinate BEFORE shared application. Never rename/edit migrations already run on a shared database, and do not apply out of order or repair checksums to suppress divergence. Private, never-shared SQL can be re-versioned only after owner/QD agreement. Additional PAY-02 migrations need their own new reservation; do not repurpose another owner's slot.

TD schema acceptance remains: required same-Order Payment/ReturnRequest composite FKs with unique targets, UNIQUE(order_id/payment_id/return_request_id) individually and UNIQUE(operation,idempotency_key), immutable one-shot SUCCEEDED ledger, NOT NULL positive decimal amount/proof/server actor/time and append-only UPDATE/DELETE protection. Create target composite UNIQUE constraints only if absent. All changes belong in TD's NEW migration, never in Receipt/Shipping migrations.

Upgrade tests must cover fresh and deployed pre-Receipt, Receipt and SHIP-03 baselines plus populated receipt/failure histories. Extend exact pending migration expectations in ReturnReceiptMigrationIT for the new ledger migration; preserve data/checksum/constraint and repeat-migrate assertions. Do not claim migration PASS until actual SQL and PostgreSQL tests run. QD's current change is documentation only.
