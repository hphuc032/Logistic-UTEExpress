# QA03 post-PR75 sync and FR-01/FR-02 preflight

**Local source identity VERIFIED; local FR02 runner PASS with UTC. FR01 joint PASS and HP acceptance remain PENDING. FR04–FR07 NOT AUTHORIZED / NOT RUN.** Original Master Plan re-read SHA25618523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a.

## Phase1 — synchronization

Actual accepted develop and PR75 merge **a5620f1971d3b684d9b05d7444f5e90442404656**. Previous69head19b7e4bdc811b7c6059ce742eb35995830f23f6b preserved by backup/qa03-before-pr75-sync-19b7e4b. Normal merge created executable source **5089a8ed17e5f8486e44847a99b309ff13d0d3b8**, tree **89c3882d470f81466b902cd3a38d7f3f272c889b**, no conflicts, no force/rebase. Accepted68/70/74/75 are all ancestors.

All629src/main object identities and28versionedSQL blobs match accepted develop exactly. Thus SecurityConfig, OTP copy/backend policy, refund guards, Shipping authority and migration history are preserved; no old production implementation reintroduced. Remaining non-documentation diff is five QA-only files: .gitignore Python-bytecode housekeeping, integrated-seed orchestration, diagnostic tests, test-only Clock config and syntheticRefundAcceptanceSeed timestamp candidate. The last changes only test-classpath synthetic PAID timestamp to persisted created_at, not production refund guards. Historical integration/evidence docs remain in the PR diff; this history/diff size is retained rather than destructively rewritten.

## FR01 — local source/build identity

FR01-source-identity.json lists source/tree, merge parents, all main-source objects, directory/workflow/POM/wrapper IDs and exact remaining diff. precommit-inspection.json lists28uniqueSQL versions and matching accepted blobs. No migration is applied by this preflight; migration set identity is verified, not upgrade-regression execution.

Build-only Maven package+classpath completed exit0/no timeout on clean5089source, with tests explicitly NOT RUN pending the gates. This is not regression PASS and is not evidence to advance FR04. Actual JAR SHA256 **ef9f481607a55f5593a5245e1ee8f818433088bd16642c3018fa3c9c410821aa**; frozen locally. Packaged OTP text is corrected; test fixture/Clock classes are excluded from production JAR. Test compilation is not test execution.

TD-independent compatibility read-back and joint confirmation are still PENDING; **FR01 is not marked PASS** merely from local hashes or CI.

## FR02 — isolated environment

Actual Java21/Mavenwrapper/Node/Python/Docker versions and OS are in environment-versions.json. Standalone helper compiled against the5089Maven test classpath, outside tracked source. DockerClientFactory ping and Testcontainers startup worked. It created a new uniquely named/labelled owned PostgreSQL17.6 container/database, bound only127.0.0.1 ephemeral port, using random in-memory credentials and scram-sha-256 host auth. Correct-password SQL returns the expected owned user/database and PostgreSQL17.6; wrong password fails28P01. Every container cleanup was verified by inspect-not-found. No secrets are printed or committed. No production database, Flyway/application startup, migration/seed/regression or business mutation is used.

### Retained preflight failures and required configuration

Attempt1 reached Docker/Testcontainers but SQL failed, with noSQLSTATE captured. Attempt2 explicitly compared localhost/IPv4 and captured22023 on both, so changing address did not fix it. Attempt3 recorded JVM defaultAsia/Saigon and the actual allowlisted PostgreSQL error: invalid value for parameter TimeZone: Asia/Saigon. Attempt4 used **-Duser.timezone=UTC**, as existing postgres-it profile does; both default-host and explicitIPv4 SQL succeeded, wrong-password denial28P01 succeeded, exit0/no timeout and cleanup succeeded. This normalizes only the isolated Java runner timezone; no auth relaxation, production policy or application Clock change. The earlier BLOCKED receipts remain. This separate FR02 timezone/startup issue is not proof of the historical Q70-S01 CONFLICT cause. Do not describe an unconfigured default-timezone runner as PASS; approved runner reproduction must includeUTC.

Earlier long-path archival copy failed before Maven started; original old89artifacts were instead preserved in a verified privateZIP. No historical test result was overwritten/relabelled new evidence.

## CI and acceptance

Source5089standard PR CI40881248445 and QA evidence tooling40881248498 SUCCESS; PR70-only integration gate40881248314 conditionallySKIPPED. Standard PR CI is separate from the **not-authorized final G02 full Java/PostgreSQL and seeds**. Publication HEAD requires its own CI check, reported in the PR handoff. No PostgreSQLCI/fullregression result is inferred from ordinary CI.

TD must read back exact final source identities, accepted-code/fixture scope, migration identity, runnerUTC/auth/cleanup and any raw-artifact receipt needed for independent review. HP must explicitly accept FR01/FR02 and authorize the next gate before FR04–FR07. Keep69Draft, G01humanPENDING and Q70-S01OPEN/Medium with ACCEPTED RISK—LIMITED; historical predicate remains unproven. No APPROVE/MERGE/QA03COMPLETE and no finalfullregression, normal/-2scombinedseed or independentrerun executed in this stage.
