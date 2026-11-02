# FR-06 QD final combined seed — pending TD/HP review

Source `5089a8ed17e5f8486e44847a99b309ff13d0d3b8`; tree `89c3882d470f81466b902cd3a38d7f3f272c889b`. Publication before execution `735a8af1d27412330cfd09ec3d228258c2193774`; accepted develop `a5620f1971d3b684d9b05d7444f5e90442404656`. This publication changes docs only. Source/index clean before/after. Separate detached checkout and private output junctions; no TD FR05 workspace/DB/evidence modified.

Per HP authorization supplied for this task: FR04 ACCEPTED; FR05 ACCEPTED WITH DOCUMENTED LIMITATION (historical cleanup evidence incomplete); FR06 AUTHORIZED; FR07 NOT RUN. Do not retroactively apply FR06 cleanup to FR05.

## Executions

| Mode | Run | Exit | Start UTC | Finish UTC |
|---|---|---:|---|---|
| normal | run-8e6f5f6a7249 | 0 | 2026-10-29T16:33:29.701037+00:00 | 2026-10-29T16:36:38.268187+00:00 |
| clock-minus2 | run-8af2e80e7d42 | 0 | 2026-10-29T16:39:11.180898+00:00 | 2026-10-29T16:42:16.122908+00:00 |

Exact commands, with actual interpreter/checkout paths, are in execution-receipt.json. Invocation shapes:

    python -B scripts/qa/rehearse_integrated_seed.py --checkout "<qa03-fr06-5089>" --disposable-demo --include-qa02-refunds --refund-clock-skew-seconds 0
    python -B scripts/qa/rehearse_integrated_seed.py --checkout "<qa03-fr06-5089>" --disposable-demo --include-qa02-refunds --refund-clock-skew-seconds -2

Normal executed ONCE; skew executed ONCE sequentially. Distinct owned containers/DBs, no pre-existing project DB used. All wrapper and four refund child application exits0; no wrapper/child timeout. Build-only and actual strict SQL preflight ran first, not a repeated FR04 suite.

## Runtime and child ownership

Explicit Java executable and Maven runtime21.0.2/Wrapper3.9.11 verified. Primary and each child actual startup logs report21.0.2; OS process observation records JDK21 path/executable checksum and UTC flag. No reliance on bare java resolution. Auth preflight verifies PostgreSQL17.6/SCRAM, correct auth, wrong-password denial28P01 and owned preflight cleanup.

Normal primaryPID13824, childPIDs43088/4240; skew primaryPID18200, childPIDs41784/9920. Normal children have no offset marker; both skew children have −2 marker; shared apps have none. Production JAR excludes fixture/Clock classes.
These are **QD runner-executed independent OS processes**, NOT TD independent execution. TD read-back/rerun/reproduction with separate receipts is **PENDING**. OS sampling times are observations, not exact child completion times; child exit comes from runner receipts, startup PID/version from original logs.

## Fingerprints / business evidence

- normal: before = after `b096807ed6dfb0fa8a7da3de6f7c6be49d103a7c949af18ec942827de4d8176b`;34 exact tables including Flyway.
- clock-minus2: before = after `8892707dccfed921d22e46a2e5f34c506fd7129273ddf5634d29f5d67905a482`;34 exact tables including Flyway.

Compare within EACH run only, never normal vs−2s equality. Counts map +global digest stable after second child and shared validate-and-preserve. Second shared HTTP writes0; legitimate live catalog edit preserved. Full shared checkpoint cross-process/crash recovery NOT RUN.
Both runs:29 successful Flyway history rows (28 versioned migrations plus schema marker), constraint/business checks pass. Shared Order states, histories, assignment/failure/RTS/review/inbox fixtures match pinned validators.
Returned-COD stays OrderREFUNDED, PaymentPAID; cancelled-paid stays OrderCANCELLED, PaymentPAID with operation-specific refs/versions. Old returned-COD ledger digest preserved. Negative checks PASS, stock/voucher/amount/history/receipt/FK invariants checked by unchanged fixture.
Shop A metrics: delivered340000, collected340000,refunds170000,netCollected170000,netCommission11900,afterCommission158100. Combined global: delivered720000,refunds550000,netCommission25200; deliberate PAID NEW ONLINE fixture correctly produces null/unavailable collection-derived metrics,unknownEvents1. No current-policy recalculation or production financial mutation.

## Cleanup / deviations (original failures preserved)

Both exact owned IDs/names verified absent by read-only inspect and filtered ps; observed original app/child processes no longer present. No unrelated resource deleted.
**FR06-E01:** external original normal observer stopped at CLEANUP_NOT_VERIFIED because case-sensitive `No such` failed to match Docker lowercase `error: no such object`. Original stopped controller/receipt/logs unchanged. Read-only exactID/name audit independently proved absence. QD disclosed stop and clarification on PR, then ran ONLY already-authorized remaining−2s with a separate external controller and case-insensitive classifier. No normal rerun, pinned runner/source/guard or business-data repair. This observer deviation remains **PENDING TD/HP REVIEW**.
**FR06-SETUP01:** initial relative junction target rejected before any seed/DB creation; absolute routing targets used; both setup attempts retained. No executable source changes.
The pinned runner computes a pg_dump hash but does not retain dump bytes. This is checksum-only evidence, NOT backup/restore verification.34-table hash covers rows including Flyway, not sequences/uploads or host recovery.

## Evidence / proposed verdict

103 original artifacts retained OUTSIDE repository in private directory `qa03-fr06-private-20261029`; private manifest maps actual paths, public manifest uses opaque IDs to avoid storage-key leakage. Archive CRC/all103 individual SHA256 verified. Original normal failure/stop and corrections retained. No raw logs/HTML/checkpoint/uploads/JAR/secrets uploaded.
Private archive SHA256 `d9b7396099c4514b67d815475e127bd5313f317b25d83bd1d3e53b8284c14717`.
Private inner manifest SHA256 `22681d8e33817c3a35e738fd50058d3e246bbdfeab11d2226c75ec6678133251`.
Package NOT DELIVERED; transfer requires approved restricted channel and TD recipient acknowledgement. Sanitized receipts do not substitute for TD raw verification.

18-case matrix:16 QD-evidence checks satisfied (cleanup with documented deviation), child case verifies QD OS independence while TD category PENDING, final TD/HP review case PENDING. No FR06 final acceptance claimed. Proposed verdict **QD TECHNICAL EXECUTION COMPLETE WITH DOCUMENTED DEVIATION / PENDING TD VERIFICATION AND HP ACCEPTANCE**.
PR69 stays Draft; G01 human PENDING; Q70-S01 OPEN/Medium—ACCEPTED RISK LIMITED, no historical cause/RESOLVED claim; QA03 NOT COMPLETED. FR07 NOT RUN.
