# QA-03 refund fixture diagnostics follow-up

Master Plan QA-03/T48/T50 and QD-owned evidence orchestration. Develop remains
fa48cb6; PRs66/68/69/70 were still open/unmerged when checked. No new P2/P3 feature,
production Order/Payment change, migration, business guard or assertion relaxation.

Q70-S01 previously lacked the exact persisted predicate of a failing TD fixture.
The QD runner now captures an explicit, bounded read-only allowlist before cleanup:
Order/Payment IDs/states/amounts/timestamps, paid count, amount match, paid-vs-created
and paid-vs-cancel timestamps, expired state, provider-reference PRESENT boolean
and existing-refund boolean. It never exports provider/proof VALUES, credentials,
cookies, private log bodies or exception messages. The capture is after process
exit/rollback, not a claim that the first historical failed transaction was observed.

Process nonzero remains a failed seed with its actual exit code. Timeout remains
a timeout and is re-raised; unavailable SQL diagnostics records only the exception
TYPE and cannot mask the original fixture failure. Three new regression tests cover
these privacy/fail-closed paths; the complete Python tooling suite is20 PASS.
These unit tests use isolated doubles, not PostgreSQL or manual browser evidence.

Successful runs also publish the allowlisted facts, enabling actual SQL projection
validation. Deliberate negative fixtures may have false predicate flags; do not
interpret all rows as eligible refund requests. No synthetic PAID timestamp is
rewritten by QD and the TD fixture/production guards are unchanged.

## New confirmed reproduction and test-only fix

Diagnostic runner ff83d54 reproduced a child exit1 and captured the failed
chronology. Cancelled Order13 has paid_at15:48:44.537819Z after
cancelled_at15:48:44.530646Z (+7,173microseconds); Order12 shows +8,521microseconds.
Both have matching205000 amounts, one PAID, nonexpired Payment and provider
reference present. This newly captured predicate explains the production CONFLICT;
it is not a retrospective observation of the first historical attempt.

TD's test-only fixture assigned synthetic paid_at using database CURRENT_TIMESTAMP,
while cancellation records Java Clock time. The two sources can produce the
invalid persisted chronology above despite the calls executing in order. Production
fail-closed behavior is correct; no production refund defect is alleged here.

Candidate d06320f changes only the fixture's synthetic paid_at/updated_at to the
persisted Payment.created_at (and adds a clarifying comment). It does not change
production Java, migrations, guards, amounts, status/history, authority or an
assertion. The earlier ff83d54 phase reused identical application/test/POM inputs
from historical full source1d8f923. After the Java test-fixture fix, a NEW full clean
Java/PostgreSQL run and combined rehearsal are executed on d06320f; their actual
results and hashes are recorded separately. No old full suite is relabeled.

Q03-G01 was retried through the supported Computer Use entry point: kernel assets
initialization still fails with Windows os error3, before UI access. No screenshots,
viewport or manual acceptance PASS. Manual375/768/1440 remains NOT RUN/BLOCKED.

Initial ADMIN bootstrap and same-process shared checkpoint scope remain PENDING
HP decisions. TD's submodule has independent-process rerun; that does not imply
shared cross-process recovery. Q70-S01 is diagnosed with a test-only fix candidate; it remains pending TD review
and verified adoption/HP disposition. It is not silently closed for PR70, whose
original HEAD688884c remains untouched. QA02 IN PROGRESS,
QA03 BLOCKED. Manager commission writes DENY ALL remains the accepted deferred scope
exception, release follow-ups NOT RUN, PAY03/VNPAY DEFERRED. No approval or merge.

## Handoff contract

TD can review/cherry-pick the isolated test-only fix commit
`d06320f43cb54f79db1bc17df3eb36da3fc71ceb` into #70 if agreed. It changes only
RefundAcceptanceSeed.java; it does not pull the QD runner or production form fix.
QD's full candidate includes unmerged #68 and original #70 through shared history.
Keep #69 Draft until dependencies and owner decisions are accepted; sync after
actual merges, not before an assumed merge. Original #70 is not silently rewritten.

A PASS of the current local execution is evidence of this candidate, not final
QA02/QA03 completion or post-merge develop verification. Historical FAIL artifacts
and source identities remain available in the earlier packet and new reproduction.

## Completed candidate execution

Actual tested source `d06320f43cb54f79db1bc17df3eb36da3fc71ceb`: **690 unit +1,388 PostgreSQL =2,078 PASS**, all139 XML report hashes and raw full logs checked, exit0/no timeout, zero failures/errors/skips. Complete Node33 and tooling20 PASS; the runner Notifications12 overlap Node33. Matching artifact combined seed first/rerun PASS on15Orders,3refunds, all34-table fingerprints equal. Both independent TD child invocations exit0 and all cancelled/PAID timing predicates are valid, with paid_at=payment_created_at. Staged fixture bytecode matches the compiled fix. Original returned-COD ledger digest is preserved. Resources cleaned up. Final publication is documentation only; CI after push is tracked separately. This does not erase pre-fix failures or imply post-merge verification.

## Current disposition — TD independent investigation update (2026-10-28)

**Q70-S01 remains OPEN / Medium.** Candidate validation is not finding closure.
TD reports independent pre-fix reproduction with external Java Clock skew -2s at
ff83d54 (CONFLICT at lockPaid:46), and candidate d06320f passing under the same
skew, including independent-process rerun. Reported focused scope:11 unit +205
PostgreSQL PASS, both refund operations, immutable evidence, inventory/voucher
and34-table fingerprints preserved. This is TD-reported evidence relayed by the
user, not another QD run; raw controlled-skew manifest/branch has been requested.

The mechanism is confirmed for controlled/new reproductions: mixed PostgreSQL
transaction-start time and Java Clock can produce paid_at>cancelled_at. The
ORIGINAL historical failure predicate was not captured, so its exact root cause
remains UNPROVEN. No previous FAIL or unknown-predicate record is rewritten.
Source-time execution JSON may say candidate verified/pending review; canonical
current finding state is [finding-disposition.json](finding-disposition.json).

Historical TD packet ed29202 was separately verified (8 published checksums) and
imported unchanged as documentation only. It describes exported tree bd0644ba,
not the new skew experiment and not this publication HEAD. Its stale #69 IDs,
9/10-Order proposal and source-identity override are historical/reference only.
Current QD implementation already has operation-specific15-Order/3-refund
acceptance and retains its real Git HEAD/clean-checkout checks. The reference
adapter was NOT executed, installed or adopted as another seed runner.

HP must review the candidate and define remaining requirements; authorized
dependency integration and fresh exact-head regression remain ahead. No new
local Java/PostgreSQL run is performed for these documentation-only changes.
Existing full2078/Node33/tooling20 evidence stays bound to source d06320f.
Production guard and all executable Git objects are unchanged. QA02 IN PROGRESS,
QA03 BLOCKED, HP Final Acceptance PENDING, PAY03/VNPAY DEFERRED. No approval/merge.
