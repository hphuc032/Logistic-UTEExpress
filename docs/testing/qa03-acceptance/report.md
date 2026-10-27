# QA-03 Ops acceptance report — 2026-10-28

**Conclusion: BLOCKED for whole QA-03 completion.** Automated Ops regression and
local operational probes PASS. Browser/manual sign-off, the integrated demo seed
and unresolved scope decisions below still need evidence or an explicit HP decision.
This packet is ready for scoped review; it is not a declaration of QA-03 COMPLETED.

## Frozen source and actual execution

- Current develop at the start of execution: `e88685213d3af20729d2e78c56568f44e0a2922c`
  (merged #61, #64 and Buyer cancellation #63).
- Actual runner SHA and tested source SHA: both `e88685213d3af20729d2e78c56568f44e0a2922c`.
- Both checkouts were clean; the runner rechecked both identities/clean trees before
  publishing its schema-v2 manifest. Final documentation commits are not represented
  as the runner/source actually executed.
- Java 21 full `-Ppostgres-it clean verify`: **686 unit/architecture tests in 73
  classes + 1,303 PostgreSQL tests in 62 classes = 1,989 PASS**; zero failures,
  errors or skips, Maven exit 0, Failsafe timeout false. **12 Node tests PASS**,
  Node and runner CLI exit 0.
- No observed closed-connection, connection-refused or forced-fork-shutdown
  diagnostics; no Failsafe dump generated. These are observations from one run,
  not a guarantee for parallel/shared containers or production.

[Full generated manifest](full-run-manifest.json) records counts, exit codes,
timeout state, runner/source/script identities and per-report/summary/raw-log hashes.
[Verification and coverage](verification-and-coverage.json) records independent hash
verification and 41 explicitly mapped assertion methods matched to executed JUnit
case names. All per-suite XML, Failsafe summary, runner-script and raw Maven/Node log
hashes were recomputed and matched. Suite names/counts exactly match merged #64's
accepted inventory: no existing suite omitted or count reduced. Existing inventory
retention does not prove every Master Plan criterion is implemented.

## Automated gates and datasets

The [checklist](checklist.md) maps Master Plan D5, T36–T50 and the QA-03 task row to
PASS/FAIL/NOT RUN/BLOCKED. Split IDs identify subcriteria; a PASS subcriterion does
not complete the unsplit Master Plan test. Every result in this packet is bound to
the source SHA above. Suite counts overlap the full-run total and are not additive.

Each integration suite creates isolated PostgreSQL 17.6 fixtures, including distinct
persisted accounts/shops/items and server-derived versions. The source-controlled
suite methods named in the checklist define exact input fixtures and assertions;
they are not evidence of a manually exercised demo database.

- Assignment: eligibility, reassignment, stale versions, post-pickup denial and
  foreign assignment isolation (ShipmentAssignmentIT 6; fulfillment guards).
- Failure/retry: append-only evidence, two-attempt limit, unchanged Order facts,
  race/rollback and exhausted delivery/physical return handling
  (ShipperOrderLifecycleIT 92, ShipperFulfillmentIT 16, ReturnToSenderIT 17).
- WebSocket/inbox: actual socket authentication, Origin/CSRF, private subscriptions,
  JWT expiry/malformed claims, revocation/lock contention, after-commit hints,
  rollback/dedup and offline inbox recovery. Single-instance scope retained
  (Socket 29; inbox 6; recovery 7; scheduler 1; client Node 12).
- Review: ownership/DELIVERED eligibility, validation/Unicode/media, duplicate and
  Review-vs-Return serialization, DB/file compensation and deferred unknown cleanup
  (ReviewIntegrationIT 25; foundation unit tests 13).
- Clean migration: DatabaseBaselineIT 1; isolated migration suites exercise deployed
  upgrades, FK/unique/CHECKs and immutable evidence. Int02HttpEnvironmentIT 5
  authenticates real USER/VENDOR/ADMIN/MANAGER/SHIPPER HTTP journeys after migration.
- Other Ops: engagement 4, account/role governance 3/6, category 8, shipping config 9,
  Ops dashboard 28, Admin revenue 26 and the existing Buyer cancellation 33 all PASS.

## Additional local operational evidence (not JUnit case counts)

[Local Ops result](local-ops-result.json) describes a real packaged-JAR rehearsal
against a newly created disposable PostgreSQL 17.6 container. The executable JAR
SHA-256 is recorded. The probe uses random synthetic credentials, localhost-only
application/DB ports and an external ignored upload directory; it never accesses
production. Raw logs, upload files and the synthetic dump stay ignored locally.

| Probe | Expected | Observed / status |
|---|---|---|
| Required ENV cleared | Fail startup, identify missing configuration | Exit 1; first unresolved key MAIL_HOST, PASS for this scenario only |
| Empty database application startup | Flyway migration then healthy app | 28 successful migrations, health UP, PASS |
| HTTP upload then process restart | Same authenticated avatar bytes after restart | Login + CSRF multipart upload; fresh login after restart; 74 bytes, same SHA-256, PASS |
| DB backup/restore | New DB has same application data | pg_dump custom archive -> pg_restore --exit-on-error; fingerprints of all 34 app tables match, PASS |
| Category used by Product cannot hard-delete | FK rejects; both references survive | Direct SQL probe on restored synthetic DB: SQLSTATE 23503, fk_products_category_id; fixture rolled back, PASS |

[Category result](category-fk-result.json) records its input dump, script, SQL and
output hashes. Both owned probe containers were removed; both local app processes
were stopped. Backup rehearsal covers this synthetic DB, not files/off-host media,
production restore, RPO/RTO or a clean deployment host. The restart used termination
of a local Java process, not proof of a graceful production service restart.
The synthetic identity is not the integrated business demo seed.

For reproducibility, save [local-ops-script.py.txt](local-ops-script.py.txt) as
`<checkout>/.work/qa03_ops_smoke.py`, and run it with Python after packaging the
frozen source. It requires this Windows JDK 21 path, Docker and postgres:17.6; it
creates/removes only its own UUID-named container and retains ignored artifacts.
The [category probe](category-probe-script.py.txt) expects the synthetic dump path
from this recorded local run; substitute the new run path deliberately for another
rehearsal. These are exact executed one-off probe sources, not general deployment
tools. Hashes match their result records. Keep local logs/dumps out of Git.

The [initial harness result](initial-harness-result.json) is retained: the first
probe wrongly required a DB placeholder to be the first startup error. The app
had already rejected missing MAIL_HOST; the harness was corrected to recognize
which required ENV failed first. No production guard was changed and no successful
run is inferred from that initial FAIL. The corrected full local probe was rerun.

## Open gates, owners and completion conditions

See [findings and gates](findings.md) for the actionable list. In particular:

1. **QD + HP/UI group:** execute and sign browser/manual Ops journeys and
   375/768/1440 checks with a frozen build and approved fixtures. No current browser
   screenshot, visual/layout, multi-tab manual or full register-to-review evidence
   exists in this packet; those are NOT RUN, not inferred from HTTP/Node tests.
2. **HP + TD + QD:** provide/run the complete ordered demo seed on an empty DB and
   rerun without reset/duplicate effects. Migrations/reference roles and synthetic
   test fixtures pass, but full SEED_MANIFEST stages 2–11 are not accepted here.
3. **HP policy, QD implementation, TD pricing review:** resolve ADMIN-06 Manager
   commission limits. Current safe denial of all Manager writes is tested. It does
   not demonstrate the planned limited-write workflow; a defined contract or an
   explicit scope decision is needed before closing that criterion.
4. **HP:** confirm final INT-02/QA gate acceptance; merging integration code and
   resolving Q02-F05 is not evidence that all manual/global QA criteria passed.

Clean-host provisioning, production ENV/TLS/cookie/storage ACL checks, off-host
backup with uploads, restore/recovery objectives, production log/alert deployment,
SMTP and release rehearsal remain NOT RUN release follow-ups (REL-01/REL-02).
They are reported separately and are not silently converted into new prerequisites
that would create a QA-03 -> REL-01 -> QA-03 cycle. HP must confirm scope and evidence
ownership at the acceptance gate. No deployment or release/tag was performed.

To mark QA-03 COMPLETED: retain valid automated evidence on the accepted source;
resolve/waive with rationale every in-scope gap; record manual Ops sign-off and full
seed evidence; attach owner decisions for shared gates; then obtain TD scoped review
and HP final acceptance. Until then the overall result remains BLOCKED.
