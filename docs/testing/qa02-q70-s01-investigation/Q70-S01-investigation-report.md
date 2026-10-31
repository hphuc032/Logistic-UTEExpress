# Q70-S01 investigation evidence for QD/HP review

**Q70-S01 OPEN (Medium). QA-02 IN PROGRESS.** This is a proposed evidence publication, with no finding closure, owner approval, merge or HP Final Acceptance. VNPAY Sandbox remains PAY-03 DEFERRED.

## Exact sources

| Identity | SHA | Meaning |
|---|---|---|
| Original combined executable source | `1d8f923b77f4dc747b4a72874b61364f33abcf36` | Original failed attempt and later observed PASS |
| Original QD evidence publication | `6cdddd249b4f7d2dd206f39487e7e35a59479b61` | Historical sanitized evidence |
| Pre-fix source | `ff83d5494d4db25533c62b120fbf86fc574de22c` | QD new reproduction and investigation before-fix checkout |
| Test-only candidate | `d06320f43cb54f79db1bc17df3eb36da3fc71ceb` | Exact after-fix source |
| PR #69 inspection snapshot | `e344ce748c65f97b94a1f0932806c14f85fb069c` | Later QD evidence publication; distinct from candidate execution |
| PR #70 | `688884cd33f4b2f4dc62f5d33a4c4002e9146660` | Production refund guard remains unchanged |
| PR #71 | `ed29202cc072e763d011189cd00ac613d564d237` | Existing evidence-only publication, unchanged by this preparation |
| PR #71 historical tested exported Git tree | `bd0644ba373c5273a09e8af4898300dc4bdd8680` | Tree of exported bytes, not a commit or this investigation source |

PR identities are the retained investigation-time snapshot, not a new claim about current remote status. The candidate changes only `src/test/java/qa02/seed/RefundAcceptanceSeed.java`: 3 additions and 2 deletions including a comment. [Exact reference diff](timestamp-fix.reference.diff.txt). Order/Payment production and migration objects match PR #70. Both built production guard classes have SHA-256 `4bb46f5e12b2891dc1dfd5a930ebecadcc1a4371880410395a6dc93a77430d94`; test fixture classes remain outside the production JAR.

## What is confirmed and what remains unknown

The original **run-9b427dbb1046 remains FAIL** with CONFLICT at `RefundAcceptanceSeed.create:149 -> CancelledPaidRefundPayments.lockPaid:46`. Its exact failed predicate was NOT CAPTURED. Original child-log SHA-256 is `9bdfb3bc2ab88551216abb70affeaf098e292d5a1a97da6799d90e439c28f4eb`; the exact child exit code was not retained by the original helper. The later same-source run-d671ae0ae88e PASS is separate evidence and cannot explain or erase the failure. **The root cause of that original individual attempt is not proven.**

QD's NEW ff83d54 reproduction captured PAID after cancellation by 7173 microseconds (synthetic Order13) and 8521 microseconds (Order12). Amounts matched, exactly one PAID existed, the Payment was not expired, provider reference was present and no refund existed. Its child exited 1. No run ID exists in the supplied sanitized reproduction records, so this packet records null rather than inventing one. [QD diagnostic comment](https://github.com/hphuc032/Logistic-UTEExpress/pull/70#issuecomment-6473748281).

The independent controlled reproduction uses [this external primary Clock bean](ClockSkewConfiguration.reference.java.txt): `Clock.offset(Clock.systemUTC(), Duration.ofSeconds(-2))`. It is added only to the TD fixture subprocess on the test classpath. Exact fixture sources, compiled fixture classes, production code and guard comparisons remain unchanged. Before-fix synthetic Order12 has paid_at after cancellation by **1989227 microseconds**; Order13 by **1992710 microseconds**, while all other captured Payment prerequisites above are valid. The child exits 1 at `lockPaid:46`; facts are captured after exit/rollback and before owned-container cleanup. This confirms a clock-mixing synthetic prerequisite defect under the controlled conditions. It does not claim a fresh unmodified natural failure.

## Before/candidate execution comparison

| Run | Recorded run ID | Exact source | Instrumentation | Result |
|---|---|---|---|---|
| naturalBefore | `run-33d1426d83b2` | `ff83d5494d4db25533c62b120fbf86fc574de22c` | Unmodified clocks | PASS; exit 0 |
| controlledBefore | `run-f5162d53820c` | `ff83d5494d4db25533c62b120fbf86fc574de22c` | Java Clock -2 seconds, external test bean | FAIL; exit 1 |
| naturalAfter | `run-b92c16b015e9` | `d06320f43cb54f79db1bc17df3eb36da3fc71ceb` | Unmodified clocks | PASS; exit 0 |
| controlledAfter | `run-45c69b98c79b` | `d06320f43cb54f79db1bc17df3eb36da3fc71ceb` | Java Clock -2 seconds, external test bean | PASS; exit 0 |

The unmodified pre-fix run PASSED and is retained as PASS. The controlled pre-fix failure is retained as FAIL/exit1. After the candidate, both the unmodified and identical controlled-skew runs pass, including two independent TD child invocations in each. Process/child exit evidence and timeouts are recorded individually in [run evidence](Q70-S01-run-evidence.json); missing fields are identified rather than fabricated.

## Clock behavior and narrowly scoped fix

PostgreSQL [CURRENT_TIMESTAMP](https://www.postgresql.org/docs/17/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT) represents transaction-start time and remains constant in a transaction; statement_timestamp represents statement-start time; clock_timestamp reads current database wall time. Java [Clock.systemUTC](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Clock.html#systemUTC()) reads the JVM's available system clock. UTC interpretation and microsecond truncation do not synchronize independent clocks. No evidence identifies the original historical host/VM clock adjustment.

The application creates Payment.created_at and cancellation facts through Java Clock. The old fixture writes synthetic paid_at/updated_at with PostgreSQL CURRENT_TIMESTAMP. Call order alone cannot ensure `Payment.created_at <= paid_at <= cancelled_at` across independent clocks. The production guard correctly rejects either violated bound.

The candidate sets synthetic `paid_at=created_at, updated_at=created_at`, using the persisted creation lower bound; equality is permitted. Both candidate executions record paid_at equal to Payment.created_at and before cancellation. Amount, proof, authority, role/ownership, lifecycle and SQL/ledger constraints are unchanged. This remains an explicitly synthetic PAID prerequisite on disposable data, not a gateway callback, real transfer or production PAID writer.

The Java21/PostgreSQL17.6 read-only comparison recorded 50 samples: 49 database timestamps inside the Java before/after bracket, one before it and none after Java-after. CURRENT_TIMESTAMP stayed identical across statements while statement time advanced 15635 microseconds and clock_timestamp advanced 15636 microseconds. These current observations do not establish a database-ahead condition or explain the original failure. [All measurements](Q70-S01-clock-comparison.json).

## Focused regression and operation invariants

**11 unit + 205 PostgreSQL PASS**, Maven exit 0, zero failures/errors/skips/rerun-or-flake markers; process and Failsafe timeouts false. Exact candidate source is d06320f. This is a focused investigation run, not a new full regression or a relabeling of earlier full runs. [Command, UTC interval, XML and log hashes](Q70-S01-focused-test-results.json). Raw XML/logs stay outside this packet.

| PostgreSQL suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| com.uteexpress.governance.AdminRevenueIT | 26 | 0 | 0 | 0 |
| com.uteexpress.order.CancelledPaidRefundIT | 61 | 0 | 0 | 0 |
| com.uteexpress.order.ManualCodRefundIT | 62 | 0 | 0 | 0 |
| com.uteexpress.order.ReturnReceiptMigrationIT | 3 | 0 | 0 | 0 |
| com.uteexpress.order.VendorRevenueIT | 46 | 0 | 0 | 0 |
| com.uteexpress.payment.CancelledPaidRefundMigrationIT | 2 | 0 | 0 | 0 |
| com.uteexpress.payment.RefundLedgerMigrationIT | 3 | 0 | 0 | 0 |
| com.uteexpress.shipping.ReturnToSenderMigrationIT | 2 | 0 | 0 | 0 |

Unit suites: ArchitectureTest 7 and CancelledPaidRefundPayloadTest 4. Covered cases include late/before-creation/missing/expired/amount/reference evidence rejection without effects, authority/CSRF, replay/duplicates/concurrency, rollback, historical receipt/ledger migrations and revenue. Existing negative-case warnings remain in retained raw evidence; no warning-free claim is made.

Both candidate rehearsals preserve cancelled-paid Order=CANCELLED/version unchanged and returned-COD Order=REFUNDED with approved receipt/version increment. Both Payments remain PAID and each completed operation has one immutable SUCCEEDED refund. No repeated inventory/voucher release occurs. Manager/proof/unpaid/NEW negatives and original-ADMIN replay pass. The dataset has 15 Orders/3 refunds; the original returned-COD ledger digest, all 34 table fingerprints and a legitimate live product edit are unchanged on rerun, with zero second-pass HTTP writes. Shared checkpoint scope remains same-process; TD fixture invocations are separate processes.

Combined finance retains shipping exclusion and deliberate ambiguity: global delivered goods 720000, refunds 550000 and commission 25200 pass; deliberate PAID NEW ONLINE ambiguity remains unavailable/null with unknownEvents=1 rather than silently excluded.

## Limitations, preservation and review request

- Original failed predicate remains unknown. New QD reproduction and controlled before/candidate evidence are separate observations.
- A passing unmodified pre-fix run is retained; no zero-flake or universal clock guarantee is claimed.
- Environment preflight and clock-probe setup failures remain in original evidence and are classified separately from Q70-S01 reproductions.
- Raw result/log/XML hashes identify retained artifacts outside this package; their bytes and original historical records are not rewritten.
- All reported investigation containers were newly owned disposable PostgreSQL17.6 containers and removed. No production code, migration, existing PR, commit, branch publication or finding state was changed during this preparation.

QD: review source identities, synthetic timestamp candidate, predicate evidence, operation invariants and proposed adoption. HP: review the historical-failure limitation and finding disposition alongside synthetic fixture/bootstrap and checkpoint scope. **Keep Q70-S01 OPEN (Medium), pending TD review/authorized adoption and TD/HP disposition.** HP Final Acceptance has not been supplied. QA-02 remains IN PROGRESS.
