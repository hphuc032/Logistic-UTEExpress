# UTEExpress

Spring Boot modular monolith for UTEExpress. The shared foundation integrates
ARCH-01, QA-00, SEC-01 and DB-01. UI-01 adds the first reusable Thymeleaf and
Bootstrap layout without implementing feature-specific business screens.

## Requirements

- JDK **21**; set `JAVA_HOME` to that JDK (the project rejects other major versions).
- Maven Wrapper pins Maven **3.9.11**; first run needs Internet access.
- Spring Boot **4.1.1**; dependencies managed by its BOM.
- PostgreSQL for application startup; integration tests use Docker and PostgreSQL 17.6.

## Run locally

Create a dedicated empty PostgreSQL database and login. Set `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD` in your shell or IDE. See `.env.example` for variable
names and placeholder values; Spring Boot does not automatically load `.env`.

Do not commit actual credentials. With a preprovisioned database, in PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/uteexpress'
$env:DB_USERNAME = Read-Host 'Database username'
$dbCredential = Read-Host 'Database password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbCredential).Password

.\mvnw.cmd package
java -Duser.timezone=UTC -jar target/uteexpress-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Stop the app with Ctrl+C. Clear the password afterward with
`Remove-Item Env:DB_PASSWORD`. Profiles local/prod use the same required ENV
credentials. Missing credentials or unavailable PostgreSQL cause startup failure.

No production database is needed for the automated test below.

Use `-Duser.timezone=UTC` for every application launch (also set this VM argument
in Eclipse/STS). PostgreSQL connections can reject legacy JVM zone aliases such
as `Asia/Saigon` before Hibernate's JDBC timezone setting takes effect. Maven
Surefire/Failsafe explicitly use UTC; this does not change the Windows timezone.

Flyway creates schema `uteexpress` and applies its schema-only baseline before
Hibernate validates mappings. Business tables are added by their module owners.

Do not set `ddl-auto=update/create` or enable Flyway clean/baseline-on-migrate.

- `GET http://localhost:8080/actuator/health` returns HTTP 200 and `UP` when healthy.
- `GET http://localhost:8080/api/v1/foundation` returns `FOUNDATION_READY`.
- Change port with `SERVER_PORT` or `--server.port=8081`.
- `GET http://localhost:8080/` renders the shared responsive landing page.
- CSS and JavaScript under `/css/**` and `/js/**` are public so anonymous visitors
  can render public pages. SEC-01 security policies remain active; real login/JWT
  belongs to AUTH-02.

## Checks

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd -Ppostgres-it verify
```

The first two commands run foundation HTTP, security and architecture tests without a database.

The HTTP/security test-only profile excludes persistence while retaining Spring Security. The final command also
packages the app and runs `DatabaseBaselineIT` against a disposable PostgreSQL
container: application boot, clean migration, validation, schema history, and
idempotent second migrate. Docker must be running; missing Docker fails the test.

Do not treat `test` or `package` alone as PostgreSQL acceptance evidence.

Linux/macOS: use `sh ./mvnw` in place of `.\mvnw.cmd` and set ENV using your shell.

Surefire reports are in `target/surefire-reports`; integration reports in
`target/failsafe-reports`; executable JAR in `target/`.

For the documented table dependency graph, run:

```powershell
powershell -NoProfile -File scripts/Test-DatabasePlan.ps1
```

## Documentation and team handoff

- [Architecture](docs/architecture.md) records architecture and SEC-01 integration.
- [Security foundation](docs/security.md) defines public/protected routes, principals and CSRF.
- [ORD-00 contracts](docs/order-contracts.md) defines Order lifecycle, checkout money,
  Shipping integration, service boundaries and pending owner decisions.
- [Shared UI conventions](docs/ui-conventions.md) defines template composition,
  Bootstrap components, responsive behavior, accessibility and output escaping.
- [Contribution workflow](CONTRIBUTING.md) explains CI, commits and PR review.
- [Database conventions](docs/DATABASE_CONVENTIONS.md) defines types, constraints,
  profiles, Flyway naming and review rules.
- [Schema and migration ordering](docs/DATABASE_SCHEMA.md) records the 30-table ERD.
- [Seed manifest](docs/SEED_MANIFEST.md) defines future demo fixture ownership and order.
- [DB-01 report](docs/DB-01-completion-report.md) records actual verification and limits.
- [ARCH-01 report](docs/ARCH-01-completion-report.md) remains historical evidence.

`docs/dependencies.txt` is a resolved dependency snapshot, not a Maven lockfile.

Regenerate it deliberately when changing dependencies. Never commit credentials,
`.env`, `target/`, `.work/`, or uploads. Work on task branches, review diffs and open
PRs into develop; do not push directly to main/develop or merge without review.