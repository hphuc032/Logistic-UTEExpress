# QA-03 findings and remaining gates

Source: `e88685213d3af20729d2e78c56568f44e0a2922c`, report date 2026-10-28.
No new Critical/High application failure was observed in executed checks. This is
not an independent security audit or a claim that missing acceptance evidence passed.

| ID | Classification / status | Owner | Evidence and closure condition |
|---|---|---|---|
| Q03-S01 | DEFERRED / ACCEPTED SCOPE EXCEPTION for current QA-03; limited-write remains open | HP policy; QD implementation; TD pricing review | HP explicitly keeps Manager commission writes DENY ALL for this QA-03 gate. Do not invent a numeric limit or mark limited-write PASS. ADMIN-06 limited-write remains a separate follow-up requiring approved policy, pricing review and implementation/security review. |
| Q03-G01 | Manual Ops/visual evidence; NOT RUN, blocks whole sign-off | QD + HP/UI group | Execute checklist steps with approved multi-role fixtures and source-bound screenshots/results; include 375/768/1440 and owner/foreign views. Prior historical browser tooling restriction is not claimed as a fresh execution here. |
| Q03-G02 | Integrated demo seed evidence; NOT RUN, blocks full T48 acceptance | HP identities/catalog; TD Commerce; QD Ops; HP coordinates | Execute all agreed SEED_MANIFEST stages on empty DB and rerun idempotently with counts/natural keys and financial/lifecycle consistency. Current isolated fixtures/reference role seed are insufficient. |
| Q03-G03 | ACCEPTED — shared automated integration baseline only | HP with TD/QD | HP accepts automated integration baseline SHA `e88685213d3af20729d2e78c56568f44e0a2922c` based on merged INT-02 and source-bound evidence. This does not sign browser/manual Ops, responsive, full demo seed or any other global criterion; those remain separately gated. |
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
Resolve G01/G02, retain the accepted S01 scope exception without marking limited-write
PASS, retain the scope-limited G03 automated-baseline acceptance, keep fresh
source-bound evidence, then obtain TD scoped review and HP final acceptance. Optional P2/P3
features and release tagging are not started by this report.
