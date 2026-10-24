# WS-02 — private realtime Order notification

Owner QD, Security reviewer HP, Order integration reviewer TD. Based on develop bce7ff0 (REV-01 #57 included). Implements HP's approved socket policy; no Order/Payment/Identity persistence or migration changes. Existing HTTP JWT/CSRF policies remain enabled.

## Transport and authority

- Native WebSocket/STOMP 1.2 at /ws, no SockJS or HTTP POST exemptions. HTTP authentication plus mandatory same-origin Origin and exactly one configured JWT cookie. Query strings are rejected. No token in URL/client JavaScript/logs.
- New Security service/DTO SocketIdentityService / SocketIdentity reuse the existing JwtDecoder signature, issuer, audience and required-claim validators. Strict socket expiry has no post-expiry clock-skew grace. Server derives account/tokenVersion/roles/expiry; credentials and the raw JWT are not retained in socket attributes.
- Handshake and inbound frames recheck persisted ACTIVE state, tokenVersion and current roles. Spring CsrfTokenHandshakeInterceptor copies the expected raw token; XorCsrfChannelInterceptor validates masked CONNECT token from the HTTP bootstrap. No CSRF disablement or replacement HTTP token strategy.
- Only SUBSCRIBE /user/queue/notifications is accepted (one subscription/session). Client SEND, direct broker/topic/foreign user destinations and identity/login/role injection are denied. Rejected frames are dropped and the connection is closed without forwarding native headers into exception logs.
- Outbound WebSocketSession decorator checks fresh authority immediately around the actual socket send, not merely when queuing a broker message. The Account lock is held through the callback, so governance cannot commit revocation between validation and send. A separate Account-only REQUIRES_NEW READ_COMMITTED transaction avoids completed after-commit resources and locks no Order/Shipment. Callback must never enter lifecycle/database mutations. No Account-first existing Order flow is introduced.
- Idle sweep is nominally every 1 second (plus database/scan latency); every outbound delivery independently checks authority. Expired, locked, token-invalidated or role-changed sessions close with policy violation. Database/authority failure closes fail-safe. Account transaction timeout is 5 seconds; Tomcat blocking send timeout and Spring send budget are 2 seconds with 32 KiB send buffer. Inbound frame limit 8 KiB; first STOMP frame deadline 10 seconds. Caps: 256 local sessions and 4 per account. No production load/latency SLA is claimed.

## Delivery and recovery

Durable Notification inserts/history projection use INSERT RETURNING and publish server-derived hints only after commit. Duplicates and rollback emit no hint. Live payload is only notification ID, constant safe title and fixed inbox link; it excludes address/phone/payment/Order details. Routing uses the trusted persisted recipient, not a client argument. HTTP inbox remains owner-scoped, no-store recovery. Hints are best effort, not exactly-once/outbox delivery; a crash after commit can lose a hint.

Authenticated layout loads a local native WebSocket client. CONNECT uses a freshly fetched masked CSRF token. It subscribes to the sole private destination and reloads the durable inbox on connect/reconnect or hint, rendering the count with textContent. Exponential jittered reconnect caps at 30 seconds; unauthorized bootstrap/policy close stops retry. Logout/pagehide closes the connection. Negotiated 10-second heartbeats and a 30-second silent-peer threshold trigger reconnect. No external STOMP client/CDN dependency.

The simple broker is local to one application instance. Production scale-out requires a separately reviewed broker/recipient delivery design; do not assume cluster fanout from this PR. Reconnect HTTP recovery works from the shared durable inbox. A proxy must forward a trusted correct scheme/host and support WebSocket upgrade; no wildcard Origin or arbitrary forwarded-header trust is enabled here.

## Verification and review gate

Focused real HTTP/WebSocket + PostgreSQL tests cover Vendor CONFIRM to Buyer delivery, commit-only minimal payload, foreign isolation, duplicate/rollback suppression, reconnect recovery, invalid issuer/audience/signature, missing/revoked JWT, missing/foreign Origin, missing/wrong masked CSRF, client SEND/foreign subscription/identity injection, strict expiry, real HTTP logout/Admin lock/role revoke, idle disconnect, outbound denial with idle sweep suppressed, Account-lock/governance blocking via pg_blocking_pids, and the account session cap. Existing SecurityHardeningIT and notification migration/recovery/scheduler tests are retained.

Node tests (node --test src/test/js/notifications.test.cjs) cover CSRF/private subscribe, durable recovery/reconnect, safe rendering, unauthorized/policy-close stop and heartbeat silence; CI includes them on Node 22. These are client logic tests, not a claim of a manual cross-browser/load test. Full Java 21 PostgreSQL clean verify and exact-head CI results are recorded on PR #58 after the final run. HP/TD review is required before merge. No production logging/alert deployment is claimed.

## Sources used to verify framework contracts

- https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html
- https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html
- https://tomcat.apache.org/tomcat-11.0-doc/web-socket-howto.html
- https://github.com/actions/setup-node

## Final local regression — 2026-10-24

Java 21 `.\mvnw.cmd -q -Ppostgres-it clean verify`: PASS, Maven exit 0, 626 unit/architecture + 1,132 PostgreSQL tests (54 IT classes), total 1,758; no failures/errors/skips/flakes, Failsafe timeout=false. All 20 NotificationSocketIT cases PASS on this final transport, including the Tomcat send-timeout configuration. Four Node tests PASS separately; JavaScript syntax check and git diff --check PASS. Existing Hikari/container shutdown and fork teardown warnings occurred without failing verification. Local log scan found no JWT-shaped values or CSRF native-header values; this is not evidence of production log/alert deployment. Do not enable cookie/native-frame TRACE logging in production.

The client tests run in a controlled JavaScript runtime and the transport tests use a real JDK WebSocket client against embedded Tomcat/PostgreSQL 17.6; no manual multi-browser, load or multi-instance delivery test is claimed. Preserve this distinction in acceptance. HP should specifically review the new Security service/DTO, Account-only transaction/lock-through-send, Origin and XOR CSRF, revocation/expiry, limits and deployment topology. TD can review unchanged Order history recipient semantics and Vendor-CONFIRM E2E. Exact-head CI and group review remain required before merge.
