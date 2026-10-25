# WS-02 final-head verification — QD

Tested source: `9f81a66fb2136b060242c802b6b82563bfcb7a6c` (PR #58). QD keeps the feature HEAD unchanged as requested by HP. This report lives on a separate documentation branch; its commit is not a new WS implementation HEAD.

## Review context

TD reported final static delta PASS from d579724 to 9f81a66; this is static review, not TD independent PostgreSQL execution. HP accepted the JWT/socket security design and single-instance scope conditionally on final review and GitHub approval/protection process. The currently connected GitHub account datcaq is also the PR author and cannot self-approve. No approval, branch-protection bypass, settings change or merge is performed by this verification.

## Reproduction

Java 21, Maven Wrapper, working Docker engine, PostgreSQL 17.6 Testcontainers:

```powershell
.\mvnw.cmd -q -Ppostgres-it clean verify
node --test src/test/js/notifications.test.cjs
```

Both commands run from a clean working tree at the tested feature SHA. Raw local logs (ignored): ws02-final-head-full-postgres.log and ws02-final-head-node.log. Sanitized XML counts/hashes appear in WS-02-final-head-evidence.json after completion. Report hashes identify the local artifacts; they are not third-party runner attestations. No raw XML properties, JWT/CSRF, private payloads or fixture log details are published.

## Evidence scope

This execution resolves the missing full-regression attribution for the actual final HEAD. It does not borrow the 1,758-test result from 4c7f878, the targeted 707-test run from d579724, or the 1,884-test combined verification branch. Those source-specific results remain separate and overlap with this suite; never add them together.

Existing real HTTP/WebSocket tests cover Vendor CONFIRM -> Buyer, trusted recipient scope, rollback/dedup, JWT claims/expiry, CSRF/Origin, revocation, Account contention and cleanup. Node tests cover the pending-refresh Low finding with delayed fetch/body and stop/error cases. This is not manual browser/load/multi-instance acceptance or a production observability rollout. The recipient policy still uses Order buyer and Shop owner at projection time; ownership transfer remains unsupported.

## Final results

**PASS on exact feature HEAD 9f81a66:** 652 unit/architecture tests (70 classes) + 1,141 PostgreSQL tests (54 classes) = **1,793 tests**, plus 12 Node tests separately. Zero failures/errors/skips or flaky cases. Maven exit=0, Failsafe timeout=false and flakes=0. Feature HEAD unchanged, working tree clean. No source/fixture/migration edits were required.

Existing closed-connection/container teardown diagnostics and fork termination after System.exit(0) appeared in the full log. XML and process status remained successful. Their infrastructure cause was not diagnosed or suppressed by this verification.

This is QD execution of the full suite, not TD independent PostgreSQL sign-off. Exact-head GitHub CI was rechecked separately before handoff. PR #58 remains unmerged; reviewer GitHub approval and HP/team final merge process remain separate gates.
