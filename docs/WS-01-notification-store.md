# WS-01 notification store and recovery

GET /notifications shows the authenticated account's latest 100 notifications. POST /notifications/{id}/read is owner-scoped, CSRF protected and idempotently preserves the first read_at. There is no public notification-write endpoint or client-supplied recipient ID. Rendering uses escaped Thymeleaf text.

NotificationService.record remains a trusted MANDATORY API for callers participating in the same business transaction. INT-02, explicitly authorized by HP, adds OrderTransitionPrepared after lifecycle history flush. OrderTransitionNotificationWriter joins that transaction with MANDATORY propagation and projects inbox rows from persisted history. An inbox write failure rolls back the Order operation. NotificationCommitted hints and the existing OrderStatusChangedEvent remain after-commit only. The committed-event listener/reconciliation still recover legacy gaps and deduplicate; they do not replace the atomic writer for new lifecycle transitions. No event-supplied recipient/state/reason is trusted as source data.

## Durable recovery without Order changes

The existing immutable, transactionally persisted Order history is the replay source. The original WS-01 implementation did not change Order; INT-02 adds only the internal pre-commit fact to its lifecycle writer. Notifications deduplicate by order-history:<historyId>:<recipientId>. Initial NEW history (from_status NULL) is excluded; transition notifications target the persisted buyer and current persisted Shop owner, with overlap deduplicated. Payloads contain only Order ID, state and fixed display text; history reason, address, phone, money and payment/token data are not copied.

The default-enabled scheduled worker runs every 30 seconds and discovers unprojected history/recipient pairs with NOT EXISTS, not a highest-ID cursor. This recovers lost in-memory callbacks, restarts, post-commit failures and a lower history ID committing after a higher ID. Each cycle processes at most 100 Orders and 200 missing notification rows per Order; remaining work stays visible for later cycles. An unavailable database delays recovery until it is available again. Throughput and notification latency depend on backlog; no exactly-once WebSocket delivery claim is made.

Recovery projects each Order in its own REQUIRES_NEW transaction, beginning with Order FOR KEY SHARE before notification recipient/Order FK checks. This retains no recipient FK locks while waiting for another Order and avoids introducing an Account-before-Order edge through a multi-Order write batch. Discovery reads do not lock business rows. Inserts are sorted by history/recipient and ON CONFLICT deduplicates concurrent workers. Recovery entry points must not be called from an active business transaction already holding locks. The new MANDATORY writer uses the already-held Order lock and neither suspends the transaction nor takes a second Order lock.

Configuration: uteexpress.notifications.reconciliation-enabled (default true), reconcile-initial-delay-ms (default 30000), reconcile-delay-ms (default 30000). Tests disable background reconciliation in test-only application.properties; NotificationSchedulerIT explicitly enables the real scheduler and verifies recovery without an event callback. There is no new table/outbox pretending to be atomic with Order; recovery depends on retained committed Order history. History retention/deletion or Shop ownership-transfer policy needs a separate contract before changing that source.

The pending migration V20261008100000__qd_ws01_notifications.sql remains newer than current develop's latest V20261008090000. NotificationMigrationIT upgrades that deployed baseline, checks validation and exactly one pending migration, and proves repeat migration is a no-op. Recheck the unmerged migration version against latest develop before merge, particularly if RETURN-01 #49 merges first.

Real-time private socket delivery belongs to WS-02 and must follow SEC-02's Origin/CSRF/JWT/expiry/revocation/destination contract. This PR adds no socket endpoint and makes no socket security claim.

Verification on 2026-10-15 with Java 21: .\mvnw.cmd -q -Ppostgres-it clean verify PASS, exit 0; 1,188 tests (423 unit/architecture + 765 PostgreSQL), zero failures/errors/skips. NotificationIT 3, NotificationMigrationIT 1, NotificationRecoveryIT 5 and NotificationSchedulerIT 1 all passed. The out-of-order regression uses two real Vendor CONFIRM lifecycle transactions, forces the lower-history-ID callback to fail after its late commit, and verifies recovery. Real scheduled execution is tested separately; other fixtures disable background jobs deterministically. No Order/Payment production file is changed by this recovery implementation.

## Recipient policy and ownership transfer

The implemented policy is **buyer plus Shop owner at projection time**, resolved from persisted rows. It is not an owner-at-event snapshot. Once inserted, a notification keeps its recipient_id; recovery never reassigns or deletes that recipient's inbox row. If ownership were changed directly, a later reconciliation could create another notification for the new owner for the same historical event, because the deduplication key includes the recipient. Existing notices would remain with the previous owner.

The supported Shop flows currently expose no ownership-transfer operation. This PR does not authorize adding one. Before enabling transfer, HP/TD/QD must approve historical visibility and recovery semantics, including whether to persist event-time recipients. An event-time policy cannot be reconstructed reliably from the current shops.owner_id. Do not describe current delivery as owner-at-event or enable transfer without resolving this privacy/retention contract.

## Reconciliation operations runbook

Current instrumentation is warning logs for callback/worker failures, not exported backlog metrics. Operations should track repeated warnings, cycle/query duration, oldest missing history age and pending recipient pairs. Run the following read-only diagnostic against a staging copy first; it can scan retained history and is intended for periodic operator diagnosis, not each 30-second worker tick:

```sql
WITH source AS (
    SELECT h.created_at,
           concat('order-history:', h.id, ':', r.recipient_id) AS dedup_key
    FROM uteexpress.order_status_history h
    JOIN uteexpress.orders o ON o.id = h.order_id
    LEFT JOIN uteexpress.shops s ON s.id = o.shop_id
    CROSS JOIN LATERAL (
        SELECT DISTINCT recipient_id
        FROM (VALUES (o.buyer_id), (s.owner_id)) ids(recipient_id)
        WHERE recipient_id IS NOT NULL
    ) r
    WHERE h.from_status IS NOT NULL
)
SELECT count(*) AS pending_recipient_pairs,
       min(source.created_at) AS oldest_pending_history_at,
       current_timestamp - min(source.created_at) AS oldest_pending_age
FROM source
WHERE NOT EXISTS (
    SELECT 1 FROM uteexpress.notifications n WHERE n.dedup_key = source.dedup_key
);
```

Alert thresholds must be chosen against the team's notification-latency objective; there is no measured production SLO yet. Compare successive samples to detect a growing backlog. Use statement_timeout on the diagnostic session and inspect EXPLAIN (ANALYZE, BUFFERS) on representative staging data before tuning discovery. LIMIT 100 bounds returned Orders, not history rows examined: discovery can become expensive as history grows, even when no notifications are missing. LIMIT 200 bounds inserts per Order, not total work scanned. Per-Order projection failures are now logged and isolated after their own transaction rollback, so later Orders in the discovered batch continue. Failed Orders remain eligible for later retries. Discovery failure still aborts the cycle and is handled by the scheduler warning log. This is not guaranteed global fairness: 100 persistently failing Orders can occupy the whole discovery page; monitor and repair recurring failures.

Do not purge history, replace discovery with a max-ID cursor, increase batches blindly, expose diagnostic SQL through HTTP, or introduce a cross-Order write transaction as a performance shortcut. Future incremental recovery/checkpoint or backlog fairness changes need their own concurrency, late-commit and recovery tests. Deployment monitoring remains an operational follow-up; this runbook does not claim metrics or production load tests were implemented.

## Sync verification — 2026-10-16

Synced with develop 5de0934 after ADMIN-07 #37 merged, without conflicts. No notification production/test behavior changed in this sync. The pending notification migration remains V20261008100000__qd_ws01_notifications.sql, after develop latest V20261008090000; RETURN-01 #49 is held until #36 merges.

Java 21 focused migration gate: .\mvnw.cmd -q -Ppostgres-it '-Dtest=ArchitectureTest' '-Dit.test=NotificationMigrationIT' clean verify PASS, exit 0 (7 architecture checks + 1 migration test). It validates upgrade from the deployed baseline, exactly one added migration and a no-op repeat.

Full gate: .\mvnw.cmd -q -Ppostgres-it clean verify PASS, exit 0; 1,199 tests (423 unit/architecture + 776 PostgreSQL), zero failures/errors/skips, Failsafe timeout=false. All 10 notification tests PASS including recovery/concurrent workers/out-of-order commits and scheduler. git diff --check PASS. Exact-head CI and HP/TD final review remain required; no merge performed. Projection-time recipient policy, transfer restriction and per-Order lock ordering remain unchanged.

## Per-Order failure isolation — 2026-10-17

A failure projecting one Order no longer aborts later Orders in the same discovered batch. Each projectOrder transaction still rolls back atomically; the outer loop catches/logs the failure and counts only committed inserts. Retry/dedup and Order-first lock ordering remain unchanged; no migration, authority/recipient policy or Order/Payment change. A real PostgreSQL constraint-injection regression covers repeated failure, progress of the next Order, recovery after repair and no duplicate inserts. Verification on 2026-10-17: Java 21 .\mvnw.cmd -q clean test package PASS. After Docker became available, .\mvnw.cmd -q -Ppostgres-it clean verify PASS, exit 0; 1,287 tests (423 unit/architecture + 864 PostgreSQL), zero failures/errors/skips, Failsafe timeout=false. NotificationRecoveryIT 6/6 PASS. Initial focused verification failed before container startup because Docker was stopped; no assertion failure from that attempt is hidden. git diff --check PASS. CI on the pushed head and HP/TD review are required before merge.
