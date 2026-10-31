# TD / HP handoff — decision required

QD completed safe post75 sync and permitted preflight only. Executed source5089a8ed17e5f8486e44847a99b309ff13d0d3b8, tree89c3882d470f81466b902cd3a38d7f3f272c889b; accepted develop/75mergea5620f1971d3b684d9b05d7444f5e90442404656. Publication metadata commit is not another execution.

TD: independently read back source/main/workflow/migration IDs and remaining QA-only fixture scope; confirm compatibility and reviewed HEAD, separating source/evidence inspection from an independently executed environment test. Check actual JAR/runner hashes and FR02 UTC/scram positive+negative SQL receipt/cleanup. State any unresolved blocker or independent execution not run. Raw logs/JAR remain private locally; no delivery is implied by this public metadata.

HP: explicitly record whether FR01 and FR02 are ACCEPTED and whether FR04–FR07 are AUTHORIZED on the agreed source/runner. A prior PR75 merge, source-only CI, or QD local preflight is not automatically this authorization.

Until then: FR01 jointPASS PENDING; FR02 localprefightPASS underUTC / acceptancePENDING; FR04–FR07NOTRUN/BLOCKED; G01humanPENDING; Q70-S01OPEN/Medium—ACCEPTED RISK LIMITED; QA03notCOMPLETE. Keep69Draft.

Reproduction: clean checkout/index at source, Java21 and generated Maven test classpath. Compile the supplied FR02Preflight.reference.java.txt as an ignored local FR02Preflight.java with that classpath; invoke with -Duser.timezone=UTC, expected source SHA and output-receipt path. It enforces clean source before/after, owns a new loopback PostgreSQL17.6 container, rejects wrong credentials and verifies cleanup. Do not substitute a production database, disable auth or suppress clean checks. No regression/seed commands are authorized by this handoff.
