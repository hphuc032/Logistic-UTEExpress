# WS-01 notification store

`GET /notifications` shows the authenticated account's latest 100 notifications.
`POST /notifications/{id}/read` marks only that account's notification read; a repeat
request preserves the original `read_at`. The POST uses the existing CSRF protection.
There is no public write endpoint or client-supplied recipient ID.

`NotificationService.record` is for trusted server-side callers and requires an
existing transaction. A caller's rollback removes the notification. The unique
`dedup_key` makes a retry idempotent. The ORD-01 status-change event is dispatched
**after** the order transaction commits, so `OrderNotificationListener` opens a
separate transaction and targets the buyer and shop owner from the database.
This event path cannot be atomic with the order transition: if notification
persistence fails after the order commit, the order remains committed and the
notification is missing. Exactly-once delivery or recovery requires a transactional
outbox / synchronous order integration in a later cross-owner change. Order creation
currently has no status-change event, so this listener handles transitions only.

The inbox is durable and can be fetched after reconnect. Real-time private WebSocket
delivery belongs to WS-02.
