# Current findings and gate disposition

| ID | Severity/status | Owner | Evidence / next action |
|---|---|---|---|
| Q70-S01 | OPEN / Medium; ACCEPTED RISK — LIMITED | TD finance, QD fixture/provenance, HP risk approver | Preserve historical FAIL. New normal/-2s89d6762 PASS narrows regression risk; does not establish historical predicate. Reopen on unexplained conflict, invalid PAID chronology, duplicate/rollback/inventory/voucher/revenue/hash/Clock/fingerprint/source/cleanup mismatch. |
| Q03-G01-ASSIGNMENT-UI-MISSING | High functional gap on accepted develop; FIXED/VERIFIED IN UNMERGED CANDIDATE | QD; TD review/HP acceptance | PR75 db3c876 on6a26370,24+12 browser and188targetedPG PASS. Review/accept/merge remains separate. |
| G01-HUMAN | Acceptance gate PENDING | Genuine QD observer / HP acceptance | Automation is not human manual. Checklist unsigned; physical devices/IME/screen-reader NOTRUN. |
| BOOTSTRAP-CHECKPOINT | Scope acceptance PENDING | HP with TD/QD | Accept synthetic initialADMIN/PAID and bounded shared checkpoint vsTD child-process reruns explicitly. Technical seed PASS is not authorization waiver. |
| PROMO03 | P1 scope/authority gap PENDING, no implicit defer | HP policy; TD service; QD Ops adapter | Vendor management routes are not platform authority. Need exact Admin/Manager scope/contract or explicit HP disposition. |
| OTP-COPY | OPEN / Low | HP UI/security | UI10minutes vsserver5minutes. QD did not modify owner source. |
| QA03-BYTECODE | RESOLVED harness housekeeping | QD | First12f runnerFAIL preserved despite2124 business tests with zero failures.89d excludes generated Python bytecode and complete runner rerun PASS. Tracked source guards retained. |
| CANDIDATE-PG-PROFILE | Corrected harness invocation | QD | First missing-profile command ran unit only, noPG; retained. Explicit postgres-it Failsafe command later188/8classes PASS. |
| BROWSER-TRANSITION/STOCK | Corrected harness/dataset issues, not product defects | QD | Prior8PASS/2FAIL/2BLOCKED retained. Freshdataset/no stock bypass +wait Bootstrap transition gives12PASS. |
| T49/RELEASE | Local probes PASS; target-specific checks NOTRUN | QD/operator; HP review | Same-app restart/media hashes and missingMAIL_HOST fail-closed do not prove target-host/TLS/offhost restore/production alerting. |

No newly confirmed production Critical/High defect beyond the tracked unmerged assignment UI gap was found in executed scopes. This is not an exhaustive whole-system or human acceptance verdict.
