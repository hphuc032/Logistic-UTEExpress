# Open findings and decisions

| ID | Severity/status | Owner | Evidence and exit condition |
|---|---|---|---|
| Q70-S01 | Medium OPEN, fixture/evidence reliability | TD + QD | Same-source first combined attempt CONFLICT, observed second PASS. Preserve both; diagnose failed Payment predicate with sanitized facts, fix fixture or get HP disposition. Do not claim zero seed flakes. |
| Q69-D01 | PENDING owner decision | HP | Initial shared ADMIN role bootstrap not yet explicitly accepted. |
| Q69-D02 | PENDING owner decision | HP | Shared module uses same-process checkpoint; TD module has cross-process rerun, not shared cross-process recovery. |
| Q03-G01 | NOT RUN / environment BLOCKED | QD + HP/UI | Computer Use kernel initialization fails; no current Ops screenshots/manual responsive evidence. |

No Critical/High finding in independently reviewed database scope. This is not a complete security/business audit. #68/#70 dependency merges and TD/HP acceptance remain pending. Manager limited-write stays deferred/accepted exception (DENY ALL); PAY03/VNPAY deferred, release follow-ups NOT RUN.

## TD investigation disposition update

Q70-S01 remains **OPEN / Medium**. TD reports candidate d06320f passing controlled Java Clock -2s verification (11unit+205PG); the original historical predicate remains unproven. See [canonical current disposition](../qa03-refund-diagnostics/finding-disposition.json). Retain candidate and all historical evidence. HP review, authorized integration and new exact-head regression remain required; no approval/merge/Final Acceptance implied.
