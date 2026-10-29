# Current post-PR70/UI02 G01 candidate verification

Exact executable source **db3c87690a84c313b7ce9abece107b0230ce96c2**, normal-merged accepted develop6a26370 (#74 UI02 and #70 refund). PR75 remains a separate unmerged candidate; none of its changes are silently included in QA03 source89d6762 or accepted develop. Backup refs/history retained; no conflict or SecurityConfig/lifecycle/payment/refund/migration edit. Existing UI02 CSS/JS/layout and Ops-detail changes preserved.

## Current execution

709 unit/architecture PASS, **188 PostgreSQL PASS in8 targeted classes**, Node48 and tooling17 PASS, zero failures/errors/skips, all exits0, no timeout. This is targeted PostgreSQL verification, not a full candidate PostgreSQL run. Historic2059 evidence belongs5538405 and is not reused here. Eight new assignment Node cases are now included in standard CI alongside the existing40cases, fixing TD's coverage finding; CI must be checked on publication HEAD separately.

The first Maven invocation omitted postgres-it: it ran unit/package only,0 PostgreSQL. Original receipt is retained; corrected classification is `profile-omission-receipt.json`. The explicit postgres-it Failsafe command subsequently ran all8classes/188cases successfully; XML hashes are in its receipt. No assertion was changed. Raw XML/private logs remain local and were hash-checked.

## G01 browser

Chrome155, headless Playwright: **24main +12fresh supplemental cases PASS** at375/768/1440. The supplemental cases cover Manager assign/reassign, invalid Review400/no-row, duplicate409/one-row, same RTS payload idempotent302/different payload409 with unchanged persisted facts, and keyboard skip-to-main/menu/table scrolling. Actor, route/status, exact source/JAR/dataset, timestamps, actual assertions, original screenshots and hashes are recorded. Tool-driven keyboard interactions remain AUTOMATED. No human signature, physical-device, IME or screen-reader result is fabricated.

An earlier supplemental dataset attempt is retained as8PASS/2FAIL/2BLOCKED: keyboard pressed a second Enter while Bootstrap was still transitioning; two late fixture requests hit stock1 below required2 (voucher23/30, not exhausted). A fresh disposable DB was used without changing stock/quota/production guards; the browser waits for actual transition completion, then all12 passed. These are harness/dataset issues, not concealed product failures.

Additional **local upload persistence check PASS**: a real UI Review upload remains publicly rendered after the owned application restarts with the same database/UPLOAD_DIR, at all3widths, with identical media SHA256 and unchanged Order/Review facts. This is not target-host/host restart/offhost restore/TLS acceptance. Both owned disposable environments were cleaned.

The packaged prod-profile missing-ENV probe exited1 with a clear unresolved required MAIL_HOST placeholder. Its initial classifier looked only for DB_URL and recorded BLOCKED; the evaluated receipt explicitly recognizes the actual required MAIL_HOST failure, without claiming a DB_URL-specific or valid production-startup test. No config change.

**G01 AUTOMATED PASS / HUMAN ACCEPTANCE PENDING**. Agent visual review is sampled, not a human visual audit. HP/TD review and explicit acceptance are required; PR75 stays Draft. OTPcopy10min vsserver5min is HP-owned Low and unchanged. Physical device/IME/screen-reader and target release checks remain NOT RUN, not implicitly deferred.
