> Current update2026-10-29: the original audit below is historical. Current gates are `qa03-final-gates.json` and `qa03-current-traceability.md`. PR66/68 are merged, developa34e8cc. G01 now has24 real automated browser PASS, not human manual acceptance; candidate fix/evidence PR75 awaits review. QD retains execution ownership. PR70 exact321ccf3 remains unmerged; final69sync/G02 waits actual merge. DOC73 is paused, not an active work item. Q70-S01 is OPEN/Medium, disposition ACCEPTED RISK — LIMITED.

# QD remaining work — Master Plan audit, 2026-10-29

Master Plan: `UTEExpress_Master_Project_Plan.docx`, SHA256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`. Original task-table TODO labels are historical, not current completion authority. This audit combines inspected source, published evidence, GitHub PR states and HP decisions. Develop checked: `fa48cb69cef92276f0dddf0d2bd396eee098f2f3`. No new application test execution is claimed.

## What exists versus what remains

| Task | Current evidence / implementation | Remaining / owner / dependency |
|---|---|---|
| ADMIN-00/01/02/03/04/05/07, DB-01, SHIP-00/01/02, ENG-01 | Implementation and handoff documents exist; account, role, catalog, Shipping, favorites/recent paths are covered in automated Ops evidence | Manual multi-role/foreign-access and responsive acceptance still belongs to QA03; existence does not independently sign every original criterion |
| ADMIN-06 | Persisted commission policy/snapshots available; DASH01/02 consume snapshots | Manager limited writes remain DEFERRED / ACCEPTED SCOPE EXCEPTION, DENY ALL. Do not implement invented limits or label full limited-write PASS |
| PROMO-03 | Vendor voucher/product promotion controllers exist; no Admin/Manager platform management controller found in inspected source | **Real remaining functional scope**: HP/TD must confirm object/roles and TD-owned management authority. QD implements UI/adapter only after boundary approval. Existing cancellation release method is not a platform-management authority |
| SHIP-03/04 | Failure/retry, evidence/coordinator and `/shipper/dashboard` exist; return-to-sender integration is separately documented in INT02 | Original Master Plan SHIP04 is Shipper dashboard, not only return-to-sender. Preserve current metric definition and run manual acceptance; do not redo completed work from old scope proposal |
| REV-01 / WS-02 / DASH-02 | #57/#58/#59 merged; automated evidence exists | QA03 browser/media/private socket/multi-role visual acceptance still separate; production media reconciliation/observability not proved by tests |
| QA-01 review support | #66 open at `7aa74a2`; prior QD APPROVED recorded | HP final acceptance/merge and any required dependency/SCA decisions remain HP-owned; do not mark COMPLETED |
| QA03 Q03-G01 | 24-case manual matrix, widths375/768/1440; zero executed screenshots | **BLOCKED**: fresh Computer Use initialization again failed before UI access with `failed to write kernel assets ... os error 3`. QD+HP/UI can execute manually or restore supported runtime; record exact source, role, screenshots and actual outcomes |
| QA03 Q03-G02 / #69 | f9c6fd6 full2078 Java/PG +33Node +23tooling, normal/-2s seed and independent TD process rerun evidence; TD scoped acknowledgement | Still PENDING HP synthetic ADMIN/PAID bootstrap and checkpoint scope acceptance; final accepted integrated-source verification after #68/#70 merges |
| Q70-S01 | HP ACCEPTED RISK — LIMITED; retained historical FAIL and reopen conditions | Not RESOLVED; keep fail-closed production guards and investigate any trigger |
| #68 -> #70 -> #69 | All remain unmerged when checked | HP decides each merge after gate. #68 **changed to c593352** from reviewed e79b760: controller+two test files changed; prior f9c6fd6 evidence does not cover this delta. Require exact-head review/CI and final verification on new accepted source |
| DOC-03 | Many module handoffs exist, no final consolidated DOC03 report found | QD can prepare chapter/ERD/evidence structure now. Final consolidation depends QA03 + HP DOC01 + TD DOC02; cannot claim final report complete |
| REL-01 | README and local/prod profiles exist | Clean-host ENV/startup, migration+seed, upload restart persistence, permissions, backup+restore, safe logs/monitoring and reproducible operator instructions remain NOT RUN. Draft checklist below is preparation, not deployment evidence |
| REV-02 | Optional P3 | DEFER until mandatory QA gates accepted; do not start while QA03 blocked |

## Immediate sequence

1. HP/TD finish #68 owner review at current HEAD; HP decides merge. QD must not self-approve own PR.
2. After actual #68 merge, TD/QD verify #70 migration/constraints, old returned ledger and business invariants on current source; HP decides #70 merge.
3. Sync #69 after both dependencies; run approved full runner, complete Node/tooling, combined normal/-2s and independent-process reruns, compare 34-table fingerprints **within each run**. Publish exact source/runner IDs, exits/timeouts/hashes and PASS/FAIL/NOT RUN. Keep #69 Draft until ready and scope accepted.
4. In parallel: resolve manual tool access or have QD+HP/UI execute the matrix; request PROMO03 contract; prepare DOC03/REL01 without claiming acceptance.

## REL01 preparation checklist — all execution NOT RUN

- Target host/OS, operator, clean checkout source and Java21/PostgreSQL17.6/Docker/Node requirements agreed.
- Provide required DB, SMTP, JWT secret, OTP pepper and MAIL_FROM via private ENV; never commit/log values. Prod cookie-secure remains true; TLS/Origin/CSRF policies unchanged.
- Run Flyway migration/validate and approved disposable/demo seed; record schema/checksum/invariants, avoid production bootstrap shortcuts.
- Persist UPLOAD_DIR outside disposable application filesystem; upload valid review images, restart app, verify authorized/public guarded display and file hashes/permissions.
- Restore both DB and uploads in an isolated target recovery environment from backup; verify references/FKs/ACL and application access. A local pg_dump hash is not restore evidence.
- Exercise safe logs/alerts and operator-controlled media reconciliation; never delete referenced or uncertain in-flight files.
- Record commands with secret placeholders, exact SHA/environment, results, rollback/recovery and sanitized evidence. Obtain HP release acceptance only after mandatory QA gates.

## DOC03 chapter preparation — not final report

Use existing handoffs as chapter sources: `ADMIN-*-*.md`, `governance-audit.md`, `SHIP-*.md`, `WS-02-implementation-status.md`, `REV-01-integration-report.md`, `REV-01-media-reconciliation-runbook.md`, `DASH-02-*.md`, `DATABASE_SCHEMA.md` and source-bound QA03 manifests. Explain governance boundaries, Order-owned lifecycle integration, lock order, DB constraints, after-commit socket dispatch, media limits, financial read-side attribution and operational limits. Cite exact tested SHA/PR per claim; label diagrams and screenshots by source. Await HP DOC01 and TD DOC02 before merging the full report. Do not copy historical TODO/scope proposals as current implemented behavior.

## Requests to teammates

- HP: #68 owner review/merge decision; PROMO03 object/role policy; #69 synthetic bootstrap/checkpoint acceptance; UI/manual execution support; DOC01 and deployment target when ready.
- TD: #68 Voucher owner review, #70 compatibility/invariants and historical QA02 findings reconciliation; define platform management boundary if HP approves PROMO03; DOC02 handoff for final consolidation.
- QD: keep evidence exact-source; complete G01 once supported/manual execution possible; post-integration regression, PROMO03 adapter after authority, DOC03 and REL01 preparation/execution at dependencies.
