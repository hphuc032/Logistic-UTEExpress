# QA03 assignment UI acceptance fix

G01 browser execution on clean disposable sourcea34e8cc confirmed missing Ops assignment controls at375/768/1440; screenshots contain fictional fixture data only. This blocks the all-UI assignment/full journey. Backend JSON assignment/reassignment remains unchanged.

Candidate adds an advisory selector for active Shippers on eligible Order detail, posts the original persisted version and masked CSRF to existing JSON endpoints using same-origin fetch, and refreshes committed facts. The server remains authority for actor/state/version/role and all locks/audit. No new endpoint, role, SQL writer, migration or lifecycle change. No action offered for NEW/unready/post-pickup; current assignee excluded from reassign choices. Pending/ambiguous requests require refresh rather than automatic replay.

Eight client safety tests pass locally; two PostgreSQL rendering/read-model regressions added to existing assignment suite. Full source-bound regression and corrected browser attempts must follow before HP acceptance. Baseline24-case attempts are automated browser evidence, not whole manual G01 acceptance; inbox selector and Review redirect expectation mistakes are harness findings, not product failures, and are retained for corrected reruns.
