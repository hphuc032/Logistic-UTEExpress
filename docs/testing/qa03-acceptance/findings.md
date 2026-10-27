# QA-03 findings and remaining gates

Source: `e88685213d3af20729d2e78c56568f44e0a2922c`, report date 2026-10-28.
No new Critical/High application failure was observed in executed checks. This is
not an independent security audit or a claim that missing acceptance evidence passed.

| ID | Classification / status | Owner | Evidence and closure condition |
|---|---|---|---|
| Q03-S01 | Business-scope gap; BLOCKED, no numeric limit invented | HP policy; QD implementation; TD pricing review | ADMIN-06/T41 requires Manager limits; current CommissionPolicyManagementService denies all Manager writes. Approve a concrete contract and test allowed/denied boundaries, or record an explicit HP scope decision. Existing deny policy is safe but does not prove limited-write functionality. |
| Q03-G01 | Manual Ops/visual evidence; NOT RUN, blocks whole sign-off | QD + HP/UI group | Execute checklist steps with approved multi-role fixtures and source-bound screenshots/results; include 375/768/1440 and owner/foreign views. Prior historical browser tooling restriction is not claimed as a fresh execution here. |
| Q03-G02 | Integrated demo seed evidence; NOT RUN, blocks full T48 acceptance | HP identities/catalog; TD Commerce; QD Ops; HP coordinates | Execute all agreed SEED_MANIFEST stages on empty DB and rerun idempotently with counts/natural keys and financial/lifecycle consistency. Current isolated fixtures/reference role seed are insufficient. |
| Q03-G03 | Shared acceptance dependency; BLOCKED pending owner confirmation | HP with TD/QD | Record INT-02 and shared QA acceptance state on the accepted implementation, including decisions for any reduced scope. A merge alone does not sign every criterion. |
| Q03-R01 | Release operations; NOT RUN | QD REL-01, HP review | Clean target-host run from README with real required ENV/TLS/cookie policy, storage ACL and persisted uploads. No deployment is performed in this packet. |
| Q03-R02 | Disaster recovery; NOT RUN | QD REL-01 + deployment operator | Off-host DB + uploads backup and restore on target host, checksums/permissions and agreed recovery objectives. Local 34-table pg_dump/restore is useful rehearsal only. |
| Q03-R03 | Production observability/media reconciliation; NOT RUN | QD + HP/operator | Exercise safe logging/alerts and operator-controlled review-media reconciliation; never delete referenced or uncertain in-flight files. Existing runbook/events are not proof of deployed monitoring. |

Q03-R01–R03 are separately reported release follow-ups, not silently invented
circular prerequisites for QA-03. HP must decide which operational criteria belong
to the current QA-03 gate; the report already remains BLOCKED by G01/G02 and scope
confirmation. No requirement may be silently waived because automated tests pass.

## Previously raised findings

- QA-02 **Q02-F05 HIGH**: resolved by accepted/merged Buyer cancellation #63.
  BuyerCancellationIT 33 passes in this exact full run; no finding is reopened.
- INT-02 **F05 LOW** teardown: #64 mitigation merged. This fresh run records zero
  tracked diagnostic occurrences and no Failsafe dump. No parallel-container or
  production reliability guarantee is inferred.
- Review filesystem orphan risk: previously accepted conditionally by HP, with
  cleanup failure events and reconciliation runbook. ReviewIntegrationIT exercises
  compensation/deferred UNKNOWN behavior; crash/off-host operator recovery remains
  operational work, not claimed complete here.
- Initial local probe **harness FAIL**, not an application defect: it expected a DB
  ENV error first but the app correctly failed earlier on MAIL_HOST. Corrected
  detection recognizes the first unresolved required setting; a fresh local run
  passed. Initial result/log hash retained; no production validation was relaxed.

## Completion decision

**BLOCKED for whole QA-03 / not READY FOR HP ACCEPTANCE as COMPLETED.**
The evidence packet is ready for TD scoped review and HP gap/scope decisions.
Resolve G01/G02, settle S01 applicability and shared gate ownership, retain fresh
source-bound automated evidence, then obtain HP final acceptance. Optional P2/P3
features and release tagging are not started by this report.
