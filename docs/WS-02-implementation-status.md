# WS-02 implementation status

Base: develop 1b3203d. This branch is an incomplete foundation, not a secured WebSocket feature or a merge-ready PR.

## Completed notification seam

Both trusted record writes and history reconciliation use INSERT RETURNING to identify newly persisted inbox rows. Deduplicated inserts produce no hint. NotificationCommitPublisher registers hints in the current transaction and publishes only after commit; rollback discards them. Payload contains only server-derived notification ID and recipient ID. Listener failures are logged without converting a committed business operation into an apparent failure. There is no network endpoint or recipient routing yet.

Hints are best effort, not an exactly-once broker delivery guarantee or a transactional outbox. A crash between commit and callback can lose a hint. HTTP inbox is durable recovery; reconnect must fetch the owner-scoped inbox. Reconciliation emits hints only for newly inserted rows, never retransmits existing rows. Future socket listeners must not write through the completed transaction or trust a recipient ID supplied by the client.

## Security handoff required before transport

HP owns JWT/Identity. Existing JwtTokenService.decode exposes user ID/tokenVersion but not expiry to business modules; HTTP filter authentication alone cannot protect an open socket. Request a service/DTO boundary for server-derived session identity, JWT expiry and fresh ACTIVE/tokenVersion/role validation without Notification importing security internals. Agree same-origin allowlist and Spring CONNECT CSRF handling. Do not exempt /ws from authentication or CSRF, accept STOMP login/recipient identity, or add permissive broker routing.

Remaining WS-02 work: authenticated handshake, CONNECT CSRF/origin, private user queue, inbound deny rules, fresh outbound authorization, idle expiry/revocation disconnect, reconnect client and real socket/PostgreSQL security tests per SEC-02. No WS-02 completion claim is made by this foundation.

## Verification

Java 21 clean verify PASS (exit 0): 599 full unit/architecture tests and 12 targeted PostgreSQL tests, no failures/errors/skips. Command: `.\mvnw.cmd -q -Ppostgres-it '-Dit.test=NotificationIT,NotificationRecoveryIT,NotificationMigrationIT,NotificationSchedulerIT' clean verify`. Tests cover commit timing, rollback suppression, insert deduplication, projection hints, failed listeners, migration, recovery and scheduler. Earlier non-clean runs were superseded to remove artifacts from the previous REV-01 checkout. Full project PostgreSQL regression and real socket security tests remain required before ready-for-review.

## Concurrent inserts and full regression — 2026-10-24

Added two real PostgreSQL races for a shared dedup key. pg_blocking_pids confirms the second INSERT waits for the first transaction. With first-writer commit, the waiting duplicate creates no row/hint. With first-writer rollback, the waiting writer inserts and publishes exactly one committed row/hint. No hint is visible while the first transaction is uncommitted. Tests use bounded waits and verify the emitted IDs against persisted rows, not caller-supplied IDs.

Full Java 21 `.\mvnw.cmd -q -Ppostgres-it clean verify` PASS, exit 0: 599 unit/architecture + 1,057 PostgreSQL tests (50 integration classes), total 1,656; zero failures/errors/skips/flakes, Failsafe timeout=false. The existing fork shutdown/Hikari closed-connection warnings occurred during teardown, without failed tests or a nonzero Maven exit. This run uses develop 1b3203d plus WS-02 foundation; pending REV-01 #57 is not included. No migration or production code changed in this update. git diff --check PASS.

This closes the foundation's full PostgreSQL verification gap, not the remaining transport/security implementation. Keep Draft until the Security boundary, socket/client implementation and real socket acceptance tests are complete. DASH-02 dependencies exist on current develop, but Ops aggregation/audit viewer is a separate branch and must not be bundled into WS-02.
