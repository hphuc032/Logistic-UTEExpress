# PR #77 — latest-source FR-10 technical verification

**AUTOMATED TECHNICAL PASS / HUMAN ACCEPTANCE PENDING.** This is an additive
execution record. It does not mark FR-10 accepted, approve the PR or authorize its
merge. HP owns Catalog acceptance; TD is the Master Plan reviewer.

## Source, sync and preservation

| Identity | Value |
| --- | --- |
| Original tested source | `a5620f1971d3b684d9b05d7444f5e90442404656` |
| Original evidence publication | `2b7d4eb780f4d48f5684a1a999a9d92c4e82f203` |
| Latest fetched develop / newly tested source | `4d57f30d7309a6d297b945b48e2d8b5a97a07cb7` |
| Tested Git tree | `f1e7a707a348f7cb77e70d5cf82e22131a631ae1` |
| Non-rewriting branch sync | `2efd307` — merge `origin/develop` into `codex/fr-10-catalog-acceptance` |
| Newly built/executed JAR SHA-256 | `0a57193273f330b028c134b196977b626c643b7582de3e7a8aefcc23046acc36` |
| Browser environment execution | 2026-11-01T06:32:13.822322Z → 06:34:49.146671Z |

The execution used a separate, clean, detached checkout at the exact develop SHA.
This PR's later documentation publication HEAD is not that execution SHA. Source
inputs (`src`, POM, Maven Wrapper and workflows) on the synced PR match develop.
The primary `D:/UTEExpress` checkout's HEAD, index hash and working status remained
unchanged through the browser run.

All **106 original tracked packet files** remain byte-identical, including the
original report, findings, checklist, drivers, screenshots, receipts, JSON and
artifact manifest. The original manifest's 105 member hashes still verify. The
historical **54 PASS / 12 FAIL** canonical result and 69 raw executions remain
historical; they were not overwritten, deleted or relabelled as this run.
See [preservation proof](historical-preservation.json).

## What changed in the runner

The original runners remain untouched. Version 2 adds explicit expected source SHA
and configurable primary repository path, proper linked-worktree index resolution,
source/primary preservation guards, source-derived migration inventory, a minimal
isolated application environment, full-matrix cardinality and duplicate-ID checks,
and exact owned-container label/ID and Java exit verification.

The browser driver's bytes and all behavioral assertions are identical to the
original final 66-case driver. No old test was removed, reclassified or weakened.
The versioned [README](README.md) documents parameters and reproduction.

## Actual execution results

| Execution | Result | Scope / limits |
| --- | --- | --- |
| Java 21 `mvnw.cmd --batch-mode --no-transfer-progress clean test` | **719 PASS**, 77 suites; 0 failures/errors/skips | Entire default unit/MVC/architecture regression, includes existing Catalog populated/genuinely-empty tests |
| `mvnw.cmd --batch-mode --no-transfer-progress -DskipTests package` | **PASS** | Builds the browser-tested JAR after successful tests; no repeated test count claimed |
| Existing four Node suites | **48 PASS**, 0 failures/skips | app, notification, Buyer cancellation and assignment client tests |
| Full unchanged Playwright matrix | **66 PASS / 0 FAIL / 0 BLOCKED / 0 NOT RUN** | Exactly the same 66 unique IDs as the historical canonical matrix |
| 375 × 812 | **22/22 PASS** | Separate fresh anonymous browser context |
| 768 × 1024 | **22/22 PASS** | Separate fresh anonymous browser context |
| 1440 × 900 | **22/22 PASS** | Separate fresh anonymous browser context |
| Fresh PostgreSQL 17.6 migration + Hibernate validate | **PASS** | All 28 source migrations applied; both app boots reached HTTP health 200 |
| Database preservation | **34/34 table fingerprints unchanged** | 0 Orders/Payments before and after; not full shared-seed acceptance |
| Cleanup | **PASS** | Exact owned container absent; both owned Java PIDs absent, checked independently after runner exit |

Java used Oracle JDK 21(+35), Node v24.22.1, installed Playwright 1.62.1, headless
Chrome **156.0.8078.74**, Docker Desktop engine 29.4.3, and PostgreSQL 17.6. The
browser version differs from the historical run and is reported rather than reused.
No new library or browser installation was performed by this task.

The [66-row matrix](coverage-matrix.md) links every real PNG to its route, viewport
and case. [Browser JSON](attempt-1/browser-results.json) retains time, observations,
diagnostics and screenshot hashes; [source proof](attempt-1/source-proof.json)
binds source/JAR/driver/runner. Cases explicitly labelled HTTP/browser-context are
not represented as click/visual interactions. Full-page screenshot height can
exceed the configured viewport height. Three representative screenshots were also
visually inspected by the agent; this is not an HP/TD signature.

[Regression results](regression-results.json) preserve individual XML hashes and
log hashes. Raw Maven logs/XML and isolated application config/logs stay outside
Git. Precise shell command start times were not separately recorded; filesystem
log times are labelled as such, not invented start/end measurements. Maven finish
times and execution exit codes were observed.

## Finding disposition

**FR10-CAT-01: TECHNICALLY RESOLVED ON TESTED SOURCE `4d57f30`; HUMAN REVIEW PENDING.**
The correction was already merged through PR #79: populated Home/category/shop
directory/shop detail use conditional `th:insert` for empty-state fragments.
`PublicCatalogControllerTest` also preserves populated and genuinely-empty
regression assertions. This task changed no production template or assertion.

All four populated routes × three viewports now contain real fixture cards and
no contradictory empty-state message (12/12 PASS). Active empty category and
approved empty shop still display their genuine empty states (6/6 PASS). Original
failure evidence is preserved in the parent packet, not retroactively resolved
there. The full matrix also retained stock visibility, nonpublic filtering,
safe error output, media scoping/headers, text escaping, guest auth/CSRF checks,
browsing navigation and keyboard/mobile-menu behavior. No new confirmed technical
Catalog failure was observed in this execution.

## Data, cleanup and scope boundary

Disabled synthetic owners, Catalog-only SQL prerequisites and the real existing
ProductDemoSeeder provided data. Fixture images are synthetic; product names,
addresses and accounts are not production data. No Render connection was opened.
SMTP health was disabled only in the isolated process because actual email
delivery is out of Catalog scope; no application configuration was modified.

[DB observation](attempt-1/database-observation.json) records matching before/after
row counts and hashes for all 34 tables. [Receipt](attempt-1/execution-receipt.json)
records the exact owned resource and cleanup commands. Java exit code 1 at cleanup
is the Windows `terminate()` stop initiated by this runner, after healthy boots
and completed checks; it is not silently reported as a natural exit or app crash.

The only net PR changes against develop remain under
`docs/testing/fr10-catalog-acceptance/`. No Order/Payment/Refund/Security/migration
behavior, production template, existing test assertion, CI workflow, PR #69 or
PR #71 is changed. QA/release gates outside FR-10 retain their existing status.
The full PostgreSQL IT profile, live deployment, real SMTP, all-role commerce
journeys and full manual accessibility/device acceptance were **NOT RUN here**.

## Remaining merge gates

1. **HP human Catalog acceptance remains PENDING.** The new source-bound
   [36-row checklist](human-acceptance-checklist.md) is entirely unsigned. Automation
   and agent screenshot inspection cannot fill these human observations/signatures.
2. **TD independent review/approval remains required.** Review runner delta,
   provenance, the resolved empty-state evidence and preserved historical failures.
3. **GitHub publication controls:** verify CI on the final publication HEAD and
   remove Draft only through the authorized human workflow. At report creation,
   the new evidence was not pushed yet; final SHA/CI is reported in PR #77 metadata
   and the task handoff, not fabricated in this pre-publication document.

`git diff --check` and manifest validation are checked before commit/push and again
on committed blobs. This task stops before GitHub approval or merge. A green CI
does not by itself accept FR-10 or complete QA-02/QA-03/release.
