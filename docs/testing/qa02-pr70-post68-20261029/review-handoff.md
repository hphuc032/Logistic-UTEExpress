# QA-02 PR70 post-PR68 integration gate — source-bound handoff

**Fresh automated gate PASS on exact source321ccf33528fbd214cf9d5399cadbc19df218148. QĐ scoped compatibility/focused review is PASS / COMMENTED, with final321 full-CI/raw-evidence/business-signoff read-back still requested. NOT READY FOR HP FINAL ACCEPTANCE until that final review/scope gate is assessed.** Manual Ops/browser remains NOT RUN by TD, not inferred from HTTP/Node/JUnit tests. No PR70/69 approval/merge or QA completion.

## Exact source and integration

- Freshly verified develop `a34e8cc3f0c0ecca4bbd08fd9f92b1d9c9247fd9` is the actual accepted PR68 merge; PR68 metadata confirms merged HEAD0bf7a4a. Original PR70 HEAD `688884cd33f4b2f4dc62f5d33a4c4002e9146660`, merge-basefa48cb69cef92276f0dddf0d2bd396eee098f2f3, ahead1/behind23 before sync.
- Normal two-parent integration merge `60b84fa46904053cff939897f40a55452e520598`, parents original688884c then currentdevelopa34e8cc3; **no conflicts/manual resolution**.
- Final source `321ccf33528fbd214cf9d5399cadbc19df218148`, Git tree `31a913354b423726e4f97d40e2233db8429fbfc3`. Ahead4/behind0 at verified target. Two QA-only files add Docker-enabled exact-source regression and encrypted artifacts; final follow-up adds sanitized/ciphertext log transport because sandbox direct-download access failed. No production guard/migration/assertion changed by sync or QA additions.
- Standard shell fetch fails at proxy127.0.0.1:9. Exact GitHub connector refs/objects were hash-verified in the isolated repository; a normal local merge tree matched the GitHub-created merge. Code publication used force=false and expected old SHA. No shell fetch/push success or history rewrite is invented.
- Main dirty QA02 checkout and all existing worktrees/historical evidence were preserved; backup `backup/pr70-688884c-before-post68` retains original source. Current isolated source `.work/pr70-post68-20261029` is clean. Evidence-only publication uses a separate branch, parent this tested source; it changes no code HEAD.

## Fresh full and targeted regression

| Gate | Actual execution / result |
|---|---|
| Full Java unit/architecture | **709 PASS**, 77 suites |
| Full PostgreSQL/Testcontainers | **1415 PASS**, 67 suites |
| Full Java total | **2124 PASS**, actual Maven process exit0, timeout=false; no failures/errors/skips/flakes |
| Separate targeted refund/ledger | **205 PostgreSQL PASS**, 8 classes, plus repeated709 unit; actual process exit0/timeout=false |
| Complete Node | **33 PASS**, exit0; standard isolation, no adapter |
| QA tooling | **17 PASS**, exit0; default Linux temporary directories, no adapter |
| Exact-head standard CI | CI166 SUCCESS; unit709 test/package overlap, Node33; equivalent synthetic tree |
| Exact-source hosted full gate | [Run40827082904 SUCCESS](https://github.com/hphuc032/Logistic-UTEExpress/actions/runs/40827082904); checks out sourceSHA directly |
| Evidence tooling CI | Run40827082990 SUCCESS;17 tests, overlapping same tooling scope |

Targeted/full/CI overlaps are not summed. Native runner process Popen.wait exits, timeouts, timestamps and clean SHA/tree guards before/after every command are recorded in execution.json. Source after all execution matches before. Raw XML testcases/counters, per-suite SHA256, summary and every raw command log were independently rehashed after local decryption. Raw snapshot/private execution-state also matches the public receipt excluding encryption metadata. No path-only provenance inference or imported XML reuse.

Environment from actual hosted run: `{"docker_client": "28.0.4", "docker_context": "default", "docker_server": "28.0.4", "java": "21.0.13", "maven": "3.9.11", "node": "v22.23.4", "python": "Python 3.13.17"}`; PostgreSQL version(s) observed `['17.6']`. Docker context/version/info all exit0 in the same GitHub-hosted Linux session as Maven. **Codex sandbox Docker access is still denied**; this success is hosted execution, not a claim of new local permissions or production validation.

| Process | Exact argv rendered for review | Start UTC | End UTC | Exit | Raw log SHA256 |
|---|---|---|---|---:|---|
| java-version | `java -version` | 2026-10-29T06:05:14.090944+00:00 | 2026-10-29T06:05:14.213651+00:00 | 0 | `477d64a6b08c9a9dd143deae108edf7929a93b40434d87285d41addc8fba509f` |
| maven-version | `sh ./mvnw -v` | 2026-10-29T06:05:14.222948+00:00 | 2026-10-29T06:05:14.597941+00:00 | 0 | `34851e2aa7d804be4e1baeca60307c95e943541c51c260b17676627b73b7cca3` |
| docker-context | `docker context show` | 2026-10-29T06:05:14.606875+00:00 | 2026-10-29T06:05:14.728777+00:00 | 0 | `01666ec060466c14b9fa06c613fbac449163f2a2017558fe16526209ab78c6b0` |
| docker-version | `docker version --format {{json .}}` | 2026-10-29T06:05:14.738290+00:00 | 2026-10-29T06:05:14.778990+00:00 | 0 | `1647560e301abf68c6e094ab63fc046cd6fd4bedf77df30597f1cf7d14438bc0` |
| docker-info | `docker info --format {{.ServerVersion}}` | 2026-10-29T06:05:14.787584+00:00 | 2026-10-29T06:05:15.009761+00:00 | 0 | `6bd60857f0062defe4edbffc3b3e49ecd7b055a2598326793fc1cfb435587e0d` |
| node-version | `node --version` | 2026-10-29T06:05:15.019008+00:00 | 2026-10-29T06:05:15.031072+00:00 | 0 | `2266957ed9a639bd7264d714bfa798a961510bf2e50695c668ab8fc0c3f4e906` |
| python-version | `/opt/hostedtoolcache/Python/3.13.17/x64/bin/python --version` | 2026-10-29T06:05:15.039310+00:00 | 2026-10-29T06:05:15.050831+00:00 | 0 | `d62a8f9cf44ca81c8ac6545117bbee8ad62521799745acf8148ef9068d61ac7b` |
| full-maven | `sh ./mvnw -B --no-transfer-progress -Ppostgres-it clean verify` | 2026-10-29T06:05:15.059195+00:00 | 2026-10-29T06:11:20.064427+00:00 | 0 | `89e306f65dff5a2ffd7863f614a4ebd97dbdd23fa1040561d5840799e7dad821` |
| targeted-refund | `sh ./mvnw -B --no-transfer-progress -Ppostgres-it -Dit.test=CancelledPaidRefundIT,CancelledPaidRefundMigrationIT,ManualCodRefundIT,RefundLedgerMigrationIT,ReturnReceiptMigrationIT,ReturnToSenderMigrationIT,VendorRevenueIT,AdminRevenueIT clean verify` | 2026-10-29T06:11:20.190510+00:00 | 2026-10-29T06:13:04.573921+00:00 | 0 | `d447a9a6a8a780d4c5c698eb41ca190a28eff8e75606ace7e9a2878fa644e1a5` |
| node-all | `node --test --test-reporter=tap src/test/js/notifications.test.cjs src/test/js/buyer-cancellation.test.cjs` | 2026-10-29T06:13:04.645564+00:00 | 2026-10-29T06:13:04.819276+00:00 | 0 | `859b4a1055c53c3ee7ca1df3dad204829b296fbbc9842027aa779ddf1c995b6a` |
| qa-tooling | `/opt/hostedtoolcache/Python/3.13.17/x64/bin/python -B -m unittest discover -s scripts/qa/tests -v` | 2026-10-29T06:13:04.829300+00:00 | 2026-10-29T06:13:05.202725+00:00 | 0 | `f3fb717711d368332f159a799f4d45124856e4f3d49482825f71eee30f8232a9` |

Targeted suites (subsets of full):

| Suite | Tests | Failure/error/skip |
|---|---:|---|
| `com.uteexpress.governance.AdminRevenueIT` | 26 | 0/0/0 |
| `com.uteexpress.order.CancelledPaidRefundIT` | 61 | 0/0/0 |
| `com.uteexpress.order.ManualCodRefundIT` | 62 | 0/0/0 |
| `com.uteexpress.order.ReturnReceiptMigrationIT` | 3 | 0/0/0 |
| `com.uteexpress.order.VendorRevenueIT` | 46 | 0/0/0 |
| `com.uteexpress.payment.CancelledPaidRefundMigrationIT` | 2 | 0/0/0 |
| `com.uteexpress.payment.RefundLedgerMigrationIT` | 3 | 0/0/0 |
| `com.uteexpress.shipping.ReturnToSenderMigrationIT` | 2 | 0/0/0 |

## Reviewed refund invariants / actual scenario coverage

- CancelledPaidRefundService creates its own READ_COMMITTED transaction, rejects an outer active transaction, and calls MANDATORY payment/authority/JDBC boundaries. No REQUIRES_NEW bypass. Advisory operation+key lock precedes Order/optional ReturnRequest -> Payment/refund -> persisted ACTIVE ADMIN authority. Role/status mutation is serialized through the Account lock; no Role lock is taken after Account.
- CANCELLED/version/history/release markers, no delivery or ReturnRequest, exactly one persisted valid PAID, positive whole-VND total, amount equality and chronology created<=paid<=cancelled<=refund remain fail-closed. ONLINE additionally requires persisted provider reference. No new provider callback/PAID writer is introduced.
- Full server-owned grand_total is refunded including shipping. Order remains CANCELLED with the same version/snapshots; Payment remains PAID. One immutable SUCCEEDED refund+audit commit atomically. Financial event after commit only, no duplicate lifecycle/inbox/shipment/stock/voucher release. Returned-COD remains a distinct operation: Order RETURNED->REFUNDED with approved receipt; Payment stays PAID.
- Namespace/canonical schema/key bytes/digest and original-ADMIN exact replay remain separate. Current persisted ACTIVE ADMIN required for first/replay. Same original request freezes the result; other actor/payload/key/order collisions cannot create another row. Existing unique Order/Payment/Return/key and composite FKs remain.
- CancelledPaidRefundIT tests7 real contender schedules and8 governance-revocation schedules with separate transactions, latches, bounded waits and actual pg_blocking_pids/worker SQL. **15 observations in full and15 in targeted** were rechecked from raw logs; neither run is combined with the other. Rollback after ledger/audit/beforeCommit restores effects/key reuse; separate-connection after-commit listener verifies committed visibility.
- Vendor/Admin revenue tests and cancelled-paid cases verify same-period merchandise net-zero, shipping excluded, signed cross-period negative amounts and zero unearned commission accrual/reversal. Wrong/unknown ONLINE evidence stays unavailable; no invented known-zero fallback.
- /admin/** and controller/service ADMIN checks, real signed JWT/rendered-cookie CSRF, forged/query/unknown field denial and version/proof validation remain. Jackson fail-on-unknown-properties and OSIV=false are retained. Promotion/Voucher binders and shared security equal accepted develop; OTP admission follows CSRF/CsrfCookieFilter and excludes refund/vendor URLs. Full PromotionBrowserFormsIT/Promotion/Voucher suites provide post68 compatibility coverage; automated HTTP is not manual Ops acceptance.

## Migration and old ledger compatibility

Static audit proves **all27 develop migrations unchanged**, one existing feature migration V20261028081110__td_qa02_cancelled_paid_refunds.sql,28 unique versions total; no guessed/new migration or backfill. New operation permits null return fields and cancellation snapshot only under its operation CHECK. Existing returned-COD evidence checks, unique/composite FKs, whole-positive-VND and immutable UPDATE/DELETE/TRUNCATE triggers remain.

Fresh/populated CancelledPaidRefundMigrationIT and old ledger/receipt/RTS migration tests actually PASS in this new runtime. They verify pending migration ordering, Flyway validation/checksums, old returned row/canonical evidence plus Order/Payment/Return rows preserved, rerun migrate=0, operation-specific version/chronology constraints, invalid foreign Payment and duplicate rejection, and append-only enforcement. Existing ManualCodRefundIT62 validates returned receipt/replay/rollback/authority behavior; opaque old-row preservation and service replay are separate assertions, not a production-ledger rehearsal. No production database, dump, trigger disable or delete/truncate of successful refunds was used.

## Raw safety, provenance and historical runs

Encrypted artifact: https://github.com/hphuc032/Logistic-UTEExpress/actions/runs/40827082904/artifacts/12877307823 ; expires `2026-11-28T06:13:18Z` (30 days). CMS DER SHA256 `419dce0751a63cc5fb59f03928e75a876e071f5b5d83719fe497b5149d53b19c`; plaintext raw archive SHA256 `e44f8029898997c0c2afa6cdf9f3ff00da5db28eba3931bf0ecb9aa1bbb43c16`. Private recipient key stays in ignored local evidence storage and was not committed/uploaded. Raw command logs/XML are snapshotted separately for full and targeted before target clean can replace them. Plaintext raw files remain local under `.work/pr70-post68-evidence-20261029/private-raw/`; public packet includes only allowlisted counts/names/versions/commands/source identities/digests. CI logs carry sanitized receipt and encrypted ciphertext only, not raw sensitive Maven/XML output.

Initial hosted source158c9c91006c54c1dc3f256db964a0657bd3bd60/run40825786384 was SUCCESS and its encrypted artifact12876991602 remains intact. Detailed counts were not read locally from that initial delivery; no counts are imported from it. The final321ccf3 execution is a separate complete rerun after QA export correction. Historical688884c2073 local PASS, QD's earlier focused/combined sources, retained original fixture FAIL and PR68 histories remain immutable; no totals/statuses are relabelled.

Q70-S01 **ACCEPTED RISK — LIMITED**, Medium historical uncertainty, [HP disposition6486631286](https://github.com/hphuc032/Logistic-UTEExpress/pull/70#issuecomment-6486631286). Original uncaptured historical CONFLICT predicate/root cause is not newly proven or RESOLVED. Production payment chronology guard is unchanged. Original #70 seed still uses CURRENT_TIMESTAMP; #69's bounded fixture candidate was not silently imported. Full combined normal/-2s/independent rerun34-table fingerprints remain the subsequent #69 gate after actual #70 integration.

## TD business signoff / independent review / manual Ops handoff

**TD technical/business signoff: PASS in the approved cancelled-paid backend/migration scope on exact321ccf3**, based on the guarded fresh full/targeted execution, locally verified raw records and preserved invariants above. No new financial-consistency blocker was identified in that examined scope. This does not attest real gateway settlement, production data, manual UI or combined-seed acceptance and is not GitHub approval/merge authority.

QD's prior review5685181122 applies to688884c and older composed sources. The new [QD review5693604981](https://github.com/hphuc032/Logistic-UTEExpress/pull/70#pullrequestreview-5693604981), attached to321ccf3, is **SCOPED PASS / COMMENTED**: independent709unit+205PG at158c9c9, old-ledger/guards/concurrency/rollback/revenue review and host full2124/Node33/tooling17 artifact-metadata checks at158. It also inspects321's9-line transport-only delta. [QD packetd37edd8](https://github.com/hphuc032/Logistic-UTEExpress/tree/d37edd8a0a244f12420c5887ead7f421b829f9df/docs/testing/qa03-pr70-post68) remains source-scoped; QD did not decrypt that hosted archive. TD's static equality proof confirms src/main,src/test,POM,.mvn and workflows unchanged158->321. This final321 fresh hosted full run and locally decrypted229XML/11log verification are separate TD evidence, not relabelled as QD independent full321 execution. The review's then-running CI observation is superseded by the actual completed321 CI metadata; final public packet/business signoff read-back remains pending. No APPROVED or merge claim.

[Manual Ops checklist](manual-ops-checklist.md) has10 human cases, all NOT RUN by TD on321: approved disposable bootstrap/actual Admin surface, refund success, preserved financial/lifecycle effects, replay/collision, actual role/revocation sessions, CSRF/validation denials, invalid-payment prerequisites, returned/old-ledger path, actual reporting periods and browser/accessibility/responsive evidence. No new refund UI form/button was found/introduced; agreed manual API-inspector workflow and named operator sign-off remain missing. QD's latest review reports G01 UI accessible on a34e8cc, with initial missing assignment/reassignment controls on Ops Order detail despite an existing API. This QD-reported observation is not TD manual execution; the earlier supported-runtime initialization blocker must not be presented as confirmed-current. Do not substitute API tests for missing UI controls or close G01.

Combined PR69 seed/Clock/recovery, real gateway transfer, production/off-host recovery/SCA/load and whole QA acceptance are NOT RUN/DEFERRED as recorded in runtime-not-run.json. QA-02 IN PROGRESS; QA-03 not completed. No #70/#69 approval/merge, finding closure or production-release claim.
