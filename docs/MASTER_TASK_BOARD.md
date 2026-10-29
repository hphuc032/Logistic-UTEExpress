# Master Task Board — repository status register

As of2026-10-29, verified develop `b538dbc3af17ff80802fa33eea187705c40059fe`. This register records only the QA-01 close-out authorized by HP; it does not recreate or change QA-02/QA-03 or other tasks' status.

## Original plan and scope of this register

The actual Master Task Board is inside the supplied external `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`, SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`. That file was read and left unchanged. No DOCX/Task Board was tracked in this repository at the audited base; this is the first version-controlled **status overlay**, not a second normative project plan. The source board's TODO is its original planning value, not evidence of current implementation state. Unknown/other task statuses are not inferred or copied as current TODO.

The QA-01 row below preserves the actual source fields. Applicable original requirements include FR-38, H1–H8, L1 and N's ownership/security/acceptance tests. Follow-up owners use the Plan's M2/Task Board and existing gate records.

| Task ID | Sprint | Feature / sub-task | Owner | Reviewer | Priority | Dependency | Original task branch | Deliverable | Current scoped status | Overall acceptance |
|---|---|---|---|---|---|---|---|---|---|---|
| QA-01 | S5 | Security acceptance — Auth/OTP/RBAC/ownership/upload/CSRF regression, evidence | HP | QĐ | P0 | INT-02 | test/qa-01-security | Security test report | **APPLICATION SECURITY REMEDIATION MERGED — ACCEPTED WITH DEFERRED GATES** | **Not COMPLETED**; SCA and applicable operational/release evidence remain outstanding |

## Accepted application-security integration

- PR [#66](https://github.com/hphuc032/Logistic-UTEExpress/pull/66), reviewed HEAD `026a29deb6695e23380d0a95c5da68564add004e`, QĐ [APPROVED](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#pullrequestreview-5692747388), standard exact-head [CI40814999100 SUCCESS](https://github.com/hphuc032/Logistic-UTEExpress/actions/runs/40814999100).
- Tested executable source `6e93d44614b1574ab2d310bae9a54ae57c385893`: HP694 unit/architecture +1346 PostgreSQL =2040 PASS. QĐ separately verified33 Node and17 tooling at reviewed HEAD; no new QĐ PostgreSQL run is claimed.
- [HP acceptance/formal NOT-RUN decision](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#issuecomment-6487721811) precedes merge `b538dbc3af17ff80802fa33eea187705c40059fe`. Scope is application-security remediation and source-bound regression/evidence only, not production/release assurance or overall QA-01 completion.
- Findings F01/F02/F05 remain recorded as resolved fixes; F03 triage/F04 fixture history remain preserved. No finding is deleted because the PR merged.

## Existing follow-up routing — no new task IDs

The authoritative owner/status/evidence/closure table is [QA-01 follow-up gates](testing/QA-01-security-report.md#deferred-and-operational-follow-up-gates). This table is a navigation index, not a change to those owners' source status packets.

| Check | Reuse existing task/gate | Owner / review | Disposition in this QA-01 acceptance record |
|---|---|---|---|
| Dependency CVE/SCA | QA-01/FR-38 → REL-01/REL-02 | HP security; QĐ review/deployment | **DEFERRED by HP; execution NOT RUN**; no clean-CVE result or waiver before production |
| Real SMTP / account-enumeration timing | AUTH-03/AUTH-04 + QA-01 → REL-01 | HP; QĐ review/deployment | **NOT RUN**, deferred operational/release verification |
| TLS / proxy / trusted peer / cookies / storage ACL | REL-01 / Q03-R01 / T49 | QĐ; HP security review | **NOT RUN**, target environment evidence required |
| Distributed budget / restart / load | QA-01 operational follow-up → REL-01 / Q03-R01 | HP security; QĐ/operator | **NOT RUN**, current single-instance/restart limitation retained |
| Wider browser/manual acceptance | UI-02 / Q03-G01 / T50 | HP UI/coordinator; QĐ Ops/reviewer; group | Existing owner gate remains open; narrower TD PR67 evidence is separate |
| Full ordered/idempotent seed | QA-03 / Q03-G02 / T48b / SEED_MANIFEST | HP/TD/QĐ by stages | Existing owner gate remains open; no seed/QA-03 completion inferred |
| Off-host DB + uploads backup/restore | REL-01 / Q03-R02 | QĐ/operator; HP review | **NOT RUN** for target/off-host acceptance; local DB rehearsal is narrower |
| Observability / clean-host / release rehearsal | Q03-R03 / REL-01 / REL-02 | QĐ deployment; HP release; TD REL-02 review | **NOT RUN by this close-out**; retain existing release gates/dependencies |

## Production/release and completion boundary

REL-01 (QD owner, HP reviewer, P0) depends on QA-01/QA-02/QA-03 under the source plan; REL-02 (HP owner, TD reviewer) depends on REL-01/DOC-03/UI-02. These are reference dependencies, not changed task statuses. Plan T48/T49/T50 still require applicable clean DB/seed, deployment and wider E2E/responsive evidence. HP expressly retained dependency review before production and did not accept deferred checks as PASS.

Each gate closes only with its specified source/build/environment-bound result, owner evidence and required review/HP decision. No deadline, release waiver, new risk acceptance or task completion is set here. This documentation PR itself requires HP final review and permission before merge; it never merges develop→main or tags/releases the application.
