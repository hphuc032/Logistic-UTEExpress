# TASK COMPLETION REPORT

Task: DB-01 — Database baseline

Owner: Quốc Đạt. Reviewer: Tiến Đạt.

Branch: `feature/db-01-schema-plan`

Base: `origin/develop` at `4c2dc14` (ARCH-01).

Status: Implementation and local verification complete; ready for human review.
Team acceptance requires PR approval and merge; delivery status is tracked in GitHub.

## Implemented

- Read Master Plan E1–E4, M2–M4, DoD, testing plan and the DB-01 task.
- Documented 30-table field/relationship inventory, owners, ERD dependency graph,
  topological migration order and machine-readable dependency manifest.
- Added JPA, PostgreSQL and Boot Flyway starter (including Flyway core via the
  starter) with versions inherited from the existing Spring Boot 4.1.1 BOM.
- Added ENV-backed datasource, dedicated uteexpress schema, UTC JDBC handling,
  Hibernate validate, disabled Open Session in View and conservative Flyway flags.
- Added timestamped schema-only migration; no business entities/tables created.
- Defined money/ID/audit/enum/version conventions and required constraints.
- Defined demo seed ordering, ownership and repeatability requirements. No fake
  executable seed for tables that do not exist yet.
- Added an explicit PostgreSQL integration check and retained the existing
  HTTP/architecture checks using a test-only persistence exclusion profile.
- Updated run instructions and resolved dependency snapshot.
- Configured Maven test JVMs for UTC and documented `-Duser.timezone=UTC` for
  application/IDE launches. PostgreSQL integration asserts session timezone UTC.

## Files created

- `.env.example`
- `docs/DATABASE_CONVENTIONS.md`
- `docs/DATABASE_SCHEMA.md`
- `docs/database-dependencies.json`
- `docs/SEED_MANIFEST.md`
- `docs/DB-01-completion-report.md`
- `scripts/Test-DatabasePlan.ps1`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/db/migration/V20260920143000__qd_db01_schema_baseline.sql`
- `src/test/resources/application-test.yml`
- `src/test/java/com/uteexpress/DatabaseBaselineIT.java`

## Files modified

- `pom.xml`: persistence/Flyway dependencies, opt-in Failsafe profile and UTC test JVMs.
- `src/main/resources/application.yml`: database settings only; existing web,
  validation/error and Actuator settings retained. HP review needed in the future PR.
- `src/test/java/com/uteexpress/FoundationHttpTest.java`: explicit test profile.
- `README.md`: actual database requirement and separate test commands.
- `docs/dependencies.txt`: current Maven dependency graph.

## Tests and build

Environment: Windows, JDK 21.0.2 selected for the commands, Maven Wrapper 3.9.11.
The machine's default `java` is JDK 24; no global Java settings were changed.
Docker Engine 29.4.1; disposable PostgreSQL 17.6. Final verification completed
at 21:22:07 Asia/Ho_Chi_Minh on 2026-09-20.

| Check | Actual result on 2026-09-20 |
| --- | --- |
| `mvnw.cmd test` | PASS: 20 tests, zero failures/errors/skips |
| Package phase of `mvnw.cmd -B -ntp -Ppostgres-it verify` | PASS: executable `target/uteexpress-0.1.0-SNAPSHOT.jar` created |
| Full `-Ppostgres-it verify` | PASS: 20 HTTP/architecture tests + 1 PostgreSQL integration test, zero failures/errors/skips |
| `powershell -NoProfile -File scripts/Test-DatabasePlan.ps1` | PASS: 30 distinct tables, known owners/references, no FK dependency cycle |
| `git diff --check` | PASS |

Build: PASS, including compilation, executable JAR packaging and integration verification.
The application context booted with real PostgreSQL. Flyway created uteexpress,
applied version 20260920143000, validated history and reported zero changes on
the second migrate. The database session timezone assertion passed.

Evidence: `target/surefire-reports/com.uteexpress.FoundationHttpTest.txt`,
`target/surefire-reports/com.uteexpress.ArchitectureTest.txt`, and
`target/failsafe-reports/com.uteexpress.DatabaseBaselineIT.txt`.
These generated reports are local build evidence and are not committed.

The earlier Docker startup issue was resolved before this run. The first real
connection then exposed PostgreSQL rejecting JVM timezone alias Asia/Saigon.
Using UTC for the test JVMs resolved that failure; the README gives the same
VM option for normal startup. No Windows timezone change or Docker reset was used.

## Security checked

- No real credentials, JWT/SMTP secrets or production/demo passwords added.
- Credentials required through ENV; test container credentials are isolated fixtures.
- Flyway clean disabled, baseline-on-migrate disabled, out-of-order disabled;
  startup must surface database/migration errors.
- HTTP error/validation checks and architecture checks still pass.
- No new route/controller, role, principal or authorization rule. Business
  authorization/ownership and entity validation are N/A for schema-only DB-01.
- No changes to other members' entities or to the Security branch.

## Remaining issues and review notes

1. No remaining local build/test blocker. Apply the documented UTC JVM option
   when launching the application outside Maven tests.
2. QD/TD must schedule commission_policies before orders; QD/HP must schedule
   categories before products. Do not silently drop either FK to fit UI timing.
3. HP should review shared application configuration and the test profile during
   integration with SEC-01. TD should review Failsafe adoption in QA-00 CI;
   ordinary `test/package` jobs alone do not run the database test.
4. Later module migrations implement FK/unique/check constraints and actual seed
   fixtures. Their business acceptance tests, including full T48, remain later work.
5. The delivery workflow is diff review, commit, push of this feature branch,
   and PR into develop. Reviewer approval and merge are separate team actions;
   this implementation does not include merging or starting another task.

Suggested commit: `feat(db): establish DB-01 database baseline and migration conventions`
