# QA-03 compatibility with merged Buyer cancellation (#63)

## Current result: scoped compatibility PASS; full local regression PASS

PR #63 was initially reviewed at `8844de24c9ac899d6a078f26ee7cacdd53cd8659`
against #64 `a26a3b0db19c0f2b294face24d7e40340becf38c`. Changed-file sets were
disjoint and the synthetic local merge was clean. Independent targeted verification:
11 unit/architecture + 96 PostgreSQL + 12 Node PASS, exit codes 0, no failures,
errors or skips. PostgreSQL suites: BuyerCancellationIT 33, Int02CommerceFlowIT 4,
ReturnToSenderIT 17, NotificationRecoveryIT 7, NotificationSocketIT 29,
ShipmentAssignmentIT 6. Exact inputs, source tree and report/log hashes are in
[the targeted evidence](QA-03-buyer-cancellation-compatibility-evidence.json).
This is compatibility testing, not a final security/business review of all #63.

During verification, HP merged #63 into develop at
`af97b3c0e5077b1ec82ea4940770edabd543da69`. QD then merged develop into #64,
without rewriting either history. Synchronized tested implementation:
`85d363a7e226b2aeb9b72e9094dde846518bef65`. Its source tree
`282f00382b77fff91bd21babdeb62a62ac0aef69` exactly matches the targeted synthetic
merge. No cancellation production code was changed by QD.

## Full evidence after sync

A clean approved runner checkout at `b71534fb5eb70d94642f6f8804ff2d0f9b0de392`
executed the full Java 21/PostgreSQL clean verify on clean source `85d363a`:

- 686 unit/architecture tests (73 classes).
- 1,303 PostgreSQL tests (62 classes), including all 33 Buyer cancellation cases.
- Java total: 1,989 PASS, zero failures/errors/skips; Maven exit 0.
- Node: 12 PASS, zero failures/skips, exit 0.
- Runner timeout flag false. No observed closed-connection, connection-refused or
  forced-fork shutdown diagnostic occurrences; no Failsafe dump generated.

[The generated v2 manifest](QA-03-post-cancellation-evidence.json) records exact
runner/source revisions, script hash, report/log hashes and exit codes. Raw log
hashes were recomputed before copying. Subsequent commits only add this evidence
and documentation; exact-head CI is a separate PR check, not inferred here.

## Status clarification

The previous [Ops report](QA-03-ops-regression.md) and manifest are historical
results on their stated source, before #63 was merged. Its note that Q02-F05 details
were unavailable describes that earlier investigation, not the current status.

Q02-F05 HIGH (Buyer unable to cancel an owned NEW Order) is addressed by TD's
merged #63. This follow-up confirms compatibility with #64's test-only context
cleanup. It is distinct from INT-02 F05 LOW (test JVM/container teardown).
No evidence here marks the whole QA-02 or QA-03 complete. Remaining manual/browser,
clean-host deployment/ENV, upload persistence, backup/restore and operational
acceptance gates remain as described in the Ops report. Cleanup remains scoped to
the sequential class execution model, with no parallel/shared-context guarantee.
