# FR-10 Catalog acceptance report

**Technical verdict: FAIL — FR10-CAT-01. Human: PENDING / UNSIGNED.** Packet ready for HP review of evidence, not final Catalog acceptance.

## Source and authority

Master Plan SHA256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`; exact FR-10 row defines Public Catalog, dependencyFR-09, HP owner/TD reviewer. HP clarified “FR-10 Catalog theo Master Plan” after the wider request used the same ID for Human Acceptance. Therefore5-role login/logout, OTP, G01/assignment, shipping/review/notification and group E2E are outside this run. Source `a5620f1971d3b684d9b05d7444f5e90442404656`, tree `797bf319420e7a82f5d2ff89859f998ae9117b35`; develop contains mergedPR75. Prior PR75 evidence is historical, not counted here. PR69 was c5ce35c at execution preflight and is now Draft at3ba76d3 (documentation only); G02 source5089a8e was not executed or accepted by this task. [Dependency matrix](dependency-matrix.md).

## Fresh actual execution

| Execution | PASS | FAIL | BLOCKED | NOT RUN | Meaning |
|---|---:|---:|---:|---:|---|
| Baseline54 viewport-cases |52|2|0|0|Two menu timing harness failures retained |
| Targeted menu recheck3 |3|0|0|0|Same3IDs repeat, fresh DB; stable transition wait |
| Diagnostic populated-empty-state12 |0|12|0|0|Confirmed UI defect, four routes at three widths |
| Final66 unique IDs |54|12|0|0|Not a single66-case rerun; provenance per case |

Raw69 records=55PASS/14FAIL including overlaps. Do not count raw69 as distinct coverage, add repeated menu PASS twice, or erase baseline FAIL.69 real PNGs, original bytes/hashes retained. All final negative availability/media tests pass in their stated scope. Agent screenshot inspection exposed the empty-state defect not asserted by the baseline.

Focused regression: **30 Java** (Architecture7 + PublicCatalogService10 + Controller7 + UiLayout6) and **7 Node** PASS; failures/errors/skips0. No full application or Maven PostgreSQL IT suite claimed. Build-only `-DskipTests package` creates the JAR; it is explicitly not a test result. [Regression receipts](regression-results.json).

Each environment: explicit Java21/UTC, Maven3.9.11, PostgreSQL17.6 in a uniquely owned loopback container, installed Chrome156.0.8078.48 and bundled Playwright1.62.1. No npm install. Actual28 SQL migrations and Hibernate validation boot; second app boot enables existing ProductDemoSeeder. Catalog-only prerequisites follow PublicCatalogIT and use DISABLED non-login owners.3 environments each retain34-table before/after fingerprints equal; Order/Payment counts0, no status manufacturing, no global/shared seed acceptance. App SMTP health disabled only for isolated Catalog runtime; no SMTP/deployment PASS.

## Defect and human gate

FR10-CAT-01 **OPEN/Medium**: four populated pages show contradictory empty state.12/12 fresh diagnostic assertions fail. HP owns correction decision; no production patch, assertion weakening, waiver or implicit accepted risk. [Finding/repro/screenshots](findings.md).

[HP checklist](human-acceptance-checklist.md) has36 observations (12groups×3widths) with blank actual/result/reviewer/time/signature. Readability, touch usability, perceived focus and error feedback need HP's real observation. Tool-driven keyboard and screenshots are not human acceptance. Catalog-only T50 evidence does not close group journey/release acceptance.

## Preservation and limitations

Primary checkout remains on DOC01 commit7dc3a88, index hashes equal before/after all three runs. Isolated execution worktree clean at each execution start/end; source/driver/JAR identities explicit. Each own Java process stopped and exact own container removed; existing resources untouched. Private logs/config/secret-bearing raw data outside repositories, hashes only published. No production Java/template/security/migration/test assertion/CI modifications. QA02 IN PROGRESS; QA03 NOT COMPLETED; G01 human PENDING, FR06 recorded ACCEPTED WITH DOCUMENTED LIMITATIONS at additive3ba76d3; FR07 AUTHORIZED / NOT RUN, TD owner. Direct decision/raw-review traceability links remain pending; no new execution or acceptance by this task. No merge/approval/PR69 mutation.

## Next actions

- **HP:** review FR10-CAT-01; authorize a separate narrow UI correction if desired; personally complete36 Catalog checklist rows on the reviewed corrected build before final acceptance. Do not accept this packet based on CI alone.
- **TD:** independently review Master Plan FR10 source/fixture/case mapping and bug reproduction; provide canonical QA02 readiness path only if release-gate work is requested separately.
- **QĐ:** retain G01/G02/FR06/FR07 and private evidence handoff gates; Catalog-only checks do not close them. Coordinate a fresh owned manual session/fixture if HP needs it.

Recommendation: **FAIL / HUMAN PENDING**, not HUMAN ACCEPTED, not QA03/release COMPLETE. No deadline or new risk acceptance invented.


## Final dependency read-back

During publication prep, PR69 advanced from c5ce35c to docs-only3ba76d3. Latest additive record states FR06 ACCEPTED WITH DOCUMENTED LIMITATIONS, TD raw verification PASS per supplied decision (not TD seed execution), and FR07 AUTHORIZED / NOT RUN, TD owner/QĐ support only. Direct HP decision and TD raw-review URL/hash remain pending traceability. This read-back preserves the original execution-time PENDING labels and all original attempts. It neither reruns those gates nor expands their acceptance. See [latest dependency observation](latest-dependency-observation.json). Develop and Catalog tested source remain a5620f1.


## Provenance precision

Baseline run1 captured accepted source/tree/JAR and clean-worktree guards before execution; its driver archive was copied after that run, not a contemporaneous recorded launch hash. Recheck2 and diagnostic3 record driver/environment-runner hashes at launch. Initial build-only start timestamp/raw log was not separately captured; terminal output records Maven duration/finish/exit. These limits are not filled with inferred timestamps or retroactive hash claims.
