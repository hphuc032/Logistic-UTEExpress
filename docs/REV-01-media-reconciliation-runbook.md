# REV-01 media reconciliation runbook

HP-approved conditional orphan risk: DB and filesystem do not share a crash-atomic commit. Rollback normally compensates stored files; deletion I/O failure or process crash may leave orphans. This runbook is OPERATOR-ONLY and does not add scheduled deletion, an HTTP cleanup endpoint or a database migration.

## Failure recording / monitoring

On definite ROLLED_BACK only, ReviewWriter compensates files; deletion failure emits ERROR with event=review_media_cleanup_failed, cleanup_id (correlation UUID), storage_key (server-generated reviews UUID path), transaction_status and failure_type, action=reconciliation_required. The original DB rollback/error is not replaced by the cleanup error. No buyer/comment/token/proof, file bytes, absolute filesystem path or exception message is logged. Keys are restricted operational metadata: these logs must not be returned to HTTP clients, placed in public PR artifacts or accessible through public media routes.

Unknown completion never deletes files: event=review_media_cleanup_deferred with reason=transaction_outcome_unknown records the key for investigation, because the DB may actually have committed. Treat it exactly like a cleanup incident; verify references and commit outcome before any action.

Operations must collect BOTH ERROR event types durably with restricted log access, alert on any occurrence and retain an incident/ticket including the correlation ID, deployment/volume identity and resolution. Local ephemeral console output alone is insufficient for production. Verify log ingestion/alerting before deployment; if unavailable, keep Review image uploads disabled operationally until logging and manual reconciliation are available. A process crash may have NO cleanup event, so include a periodic OFFLINE inventory review or incident-triggered scan. This does not authorize automated removal.

## Absolute safety rule

**Never delete or quarantine a referenced file, and never act on an orphan candidate while any writer/transaction could still commit a reference to it.** File age, an old cleanup event or NOT EXISTS in one snapshot is not proof of safety. Hidden Reviews count as references; include every row in review_media without visibility/Order/Product filters. Use the primary database, not a lagging replica. If DB/logging/maintenance coordination is unavailable or uncertain, stop with no file mutation.

## Procedure (dry-run first)

1. Identify the correct environment, primary DB and canonical shared UPLOAD_DIR/volume, with backup/recovery owner. Do not mix dev/test/prod, volumes or tenants. Obtain normal operational approval before changing files.
2. Enter maintenance: disable ALL Review/image writers and imports/admin scripts across every app instance/job sharing the volume. Stop or drain in-flight requests and transaction callbacks, including paused workers. Include crash-restarted instances; do not merely disable one pod or public route. Ensure no new writer can start during reconciliation.
3. Confirm all relevant runtime-role sessions are drained (no active or idle-in-transaction work); inspect pg_stat_activity on the primary, excluding only the inspection connection. Agree runtime role/app instance inventory with operations. If any unknown writer, transaction, held lock or uncertain commit outcome remains, do not proceed. Read-only inspection SQL:

```sql
SELECT pid, usename, application_name, state, xact_start, query_start
FROM pg_stat_activity
WHERE datname = current_database()
  AND pid <> pg_backend_pid()
  AND xact_start IS NOT NULL
ORDER BY xact_start;

SELECT storage_key FROM uteexpress.review_media ORDER BY storage_key;
```

4. Generate a DRY-RUN inventory of canonical regular files under only UPLOAD_DIR/reviews matching the application's UUID jpg/png/webp key format. Do not follow symlinks or scan/delete other namespaces. Do not treat temporary files, unknown formats, directories, manifests or arbitrary log paths as final media candidates. Record relative key, volume, size, mtime and digest in a restricted incident artifact. Candidate must be unreferenced AND at least 24 hours old, but the grace period is extra protection only, never a substitute for maintenance/drain.
5. Cross-check the full reference set again on the primary in a fresh read AFTER drain and immediately before EACH proposed move. Check every review_media row, including HIDDEN; no review/user/order/status filter. Verify known consumers of reviews namespace have not expanded since this runbook. Stop on unresolved/dangling references, unexpected path shape, symlink, changed metadata/digest or any new transaction/writer. Do not manufacture rows or delete referenced files to repair a missing asset.
6. Produce a human-reviewed candidate manifest. Recheck canonical source/destination containment within the explicitly approved volume before any file operation. Prefer reversible quarantine in an access-restricted directory OUTSIDE served namespaces, preserving relative key/digest and restore mapping. Use one filesystem tool/shell end-to-end; no string-built deletion or cross-shell path handoff. This application does not supply an auto-delete command.
7. Before resuming writers, repeat reference/volume checks; restore immediately if a quarantined key is referenced or any doubt remains. Verify referenced Review media still serves correctly and hidden media stays blocked. Record operator/time/manifest and resolve the cleanup event; retain quarantine for at least 7 days per deployment retention policy. Retention is not a deletion authorization.
8. Permanent deletion, if later authorized, is a SEPARATE maintenance/drain + primary recheck procedure over the unchanged reviewed manifest. Never assume a week in quarantine makes a file safe. Backups/restore mapping must remain available. If unable to repeat the safety checks, keep quarantine; do not delete.

## Recovery / limitations

A referenced but missing/unreadable file is an availability incident: restore from backup or investigate with the owner; never remove the DB reference or fabricate replacement content merely to make a scan clean. Cleanup error only authorizes investigation, not deletion. Successful compensation and error recording are tested; actual production log retention, maintenance controls, backup and manual execution must be verified by deployment operators. Disk pressure is a reason to escalate, not bypass live-reference/commit safety.
