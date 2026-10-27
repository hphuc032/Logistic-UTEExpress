# QA-02 evidence manifest — Q02-F05 fix

Base develop: `798535b32ff81a011f34cfa01bbeb2d2dd0cb981`. Exact tested source HEAD: `b6c832785d87c3af10008a7c357389ef685f2123`.
Branch: `fix/q02-f05-buyer-cancellation`; worktree: `C:/Logistic-UTEExpress/.work/q02-f05-fix`.
Owner/executor: TD. HP retains final acceptance and merge authority.
Q02-F05: **FIXED, LOCAL REGRESSION VERIFIED**. QA-02 remains **IN PROGRESS**, pending
publication/exact-head CI, broader original-plan comparison and HP final acceptance.
No QA-02 COMPLETED, remote CI or merge is claimed.

Required full commands, executed in the fix worktree with Java 21:

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test --test-reporter=tap src/test/js/notifications.test.cjs
git diff --check
```

Fresh full result: Maven exit **0**, Surefire **682** / 72 classes +
PostgreSQL Failsafe **1303** / 62 classes = **1985 Java tests**.
Node exit **0**, **12/12 PASS**. Zero failures/errors/skips/flakes/rerun markers;
Failsafe timeout=false, completed=1303. The success result attribute is `null` per the
installed Surefire API 3.5.6, failureMessage=nil. Focused counts overlap and are not added.
Environment: Windows PowerShell, Temurin Java 21.0.12.1+1 LTS, Maven Wrapper 3.9.11,
Spring Boot 4.1.1, PostgreSQL 17.6 Testcontainers on Docker Desktop Linux engine,
Node 22.11.0; JVM tests UTC, financial periods Asia/Ho_Chi_Minh.
Fresh/upgrade migration suites and Hibernate validate PASS; 27 fresh migrations through
`20261027100000`, populated history/checksum preservation and repeat migrate=0 verified.
No migration, financial formula, SQL projection or forbidden owner-module source changed.


Sanitized [run evidence JSON](QA-02-run-evidence.json), SHA-256 (canonical UTF-8/LF)
`9366f2177c691832def01ad6acbefae1685c185fc0b64485be229ebb5dc876f5`. It records all tracked executable/build/test input hashes, per-suite
counts and raw artifact paths/sizes/digests. Source HEAD and every input were rechecked
unchanged after Maven/Node; no old source report is counted as current evidence.
The JSON digest matches the staged Git blob. For a Windows checkout that converts
JSON to CRLF, normalize CRLF to LF before checking this JSON digest. Raw diagnostic
artifact hashes below always cover their exact original bytes without normalization.

## Raw evidence retained locally, excluded from commits

| Path | SHA-256 |
|---|---|
| `target/failsafe-reports/failsafe-summary.xml` | `156c0099537619f055e218ba3c5436f1a9028cc3775e62526722d0bdd117b798` |
| `.work/qa02-fix/node.tap` | `fa97c713b17aed4af25a1db6dc9a8ba09d57a953219af1150de37dc73f82639f` |
| `.work/qa02-fix/full.log` | `006ba190174789fa45192b3372320bd266dbf77106e8ed6d22d2cd9ae87e8d75` |
| `.work/qa02-fix/full.exit` | `13bf7b3039c63bf5a50491fa3cfd8eb4e699d1ba1436315aef9cbe5711530354` |
| `.work/qa02-fix/node.exit` | `13bf7b3039c63bf5a50491fa3cfd8eb4e699d1ba1436315aef9cbe5711530354` |
| `.work/qa02-fix/full-start.json` | `834cf4031abf2a1877b97f76c2b55d96b851f202e968c24ecf86cc6e03a2cae5` |
| `.work/qa02-fix/collect.py` | `3ccab5e85a0be4212c04cc614171129ff5143da1d58df0e4e71b5a35a4c16d63` |

Per-suite XML hashes are in the JSON. Raw log/XML may contain synthetic credentials,
request cookies/proof values and test properties; only paths/counts/hashes are exported.
No target artifact, JWT, database dump, environment dump or raw private log is staged.
Focused raw evidence: `.work/qa02-fix/focused-final.log`, `focused-final.exit`,
`focused-final/`. Initial compile failure and earlier focused pass are separately kept,
not summed into full totals. Full source freeze/start: `.work/qa02-fix/full-start.json`.
Node TAP: `.work/qa02-fix/node.tap`; both process exit files are 0.

Parser checks fresh XML after start, testcase/suite/summary counts, required suites,
zero failures/errors/skips/flaky/rerun elements, summary timeout=false/result=null,
nil failureMessage and nonzero completed count, Node tests=pass>0 and remaining
counters zero. It verifies executable SHA-256/HEAD before publishing. This local Python
extraction is not execution of unmerged QA-03 tooling and is not a third-party attestation.

The original checkout remains on `test/qa-02-order-payment-regression` at
`d1cfaeaee2a5bb8afa4edab29efbf050a209620e`, including its uncommitted red reproduction,
reports/evidence and protected `tatus --short`. Original QA file hashes were recorded
and rechecked; no reset, clean, stash, deletion, edit or commit was applied there.
Protected-file SHA-256: `f4ce4efb9b7e36af6006529687b600abf8640da96b918a06fce2ed2b065804a4`.
Only selected QA tests/reports were copied into the separate fix worktree. The historical
1951/12 PASS and 8-case red reproduction remain historical, not this fix's test totals.


Teardown diagnostics remain: 484 closed-connection lines and
1 30-second fork-shutdown lines. Exit/XML PASS is distinct from clean teardown.
Reports/JSON record the exact tested source above. A subsequent documentation-only
publication commit has a separate final PR HEAD; it does not replace the tested SHA
or imply another full run or exact-head CI. No push/PR/merge occurred.
