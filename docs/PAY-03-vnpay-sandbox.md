# PAY-03 — VNPAY Sandbox MVP

This change is prepared locally for the existing Spring Boot service at
`https://uteexpress-web.onrender.com`. It does not deploy or change Render settings.
The gateway is fixed to `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
Even under the Spring `prod` profile it only uses VNPAY Sandbox.

## Configure Render

Keep the existing database, JWT, OTP and mail environment settings. Add or verify:

- `VNPAY_ENABLED=true`.
- `TMN_CODE` and `HASH_SECRET`: the existing VNPAY **Sandbox** credentials in Render Environment.
  Alternatively use `VNPAY_TMN_CODE` and `VNPAY_HASH_SECRET`; these take precedence.
  Do not put real values in source, screenshots, logs or chat.
- `VNPAY_RETURN_URL=https://uteexpress-web.onrender.com/payments/vnpay/return`
  (also the default).

Register the following IPN URL in the VNPAY **Sandbox merchant configuration**:
`https://uteexpress-web.onrender.com/payments/vnpay/ipn`.
VNPAY does not take this URL from the generated checkout query string.
The merchant must allow this website/Return domain. Do not register a production terminal.

`VNPAY_ENABLED` defaults to false so an environment without Sandbox credentials can
still run COD. Enabling it requires valid configuration. Spring does not auto-load `.env`.
No migration or schema change is needed: the existing ONLINE method, attempt key,
provider reference, timestamps and uniqueness constraints are reused.

## Routes and flow

- Existing checkout: `GET /user/checkout/view`; request a quote and choose **VNPAY Sandbox**.
- Existing placement form: `POST /user/checkout/view/place-order` with `paymentMethod=ONLINE`.
  Buyer identity, totals, shipping and prices remain server-derived; CSRF is still required.
- URL initiation: `GET /orders/{orderId}/payments/vnpay`, authenticated USER/VENDOR owner only.
  It reads the one persisted attempt, then redirects to Sandbox. Repeating it reuses the same
  reference and original 15-minute validity window; it never creates another attempt.
- Public Return: `GET /payments/vnpay/return`. Verifies the signed callback and matches stored
  reference/amount. Displays persisted PAID, waiting or closed status. It never updates Payment.
- Public IPN: `GET /payments/vnpay/ipn`. Returns JSON with exact `RspCode` / `Message` keys.
  No authentication or CSRF token is needed for these two GET callbacks. Other routes retain security.
- Existing JSON checkout also accepts `paymentMethod=ONLINE` when enabled; use its returned `orderId`
  with the authenticated URL-initiation endpoint. The placement response DTO is unchanged.

IPN verifies HMAC-SHA512 (sorted form-encoded `vnp_` fields; excludes hash/hash-type), merchant,
persisted attempt-to-order mapping, amount ×100, persisted Order/Payment amount equality,
transaction number, both response/status codes, and successful payment time within the original
attempt window. Only response `00` **and** transaction status `00` can set PAID.
Delayed IPN delivery is accepted when the signed payment time was within that window.

IPN locks Order then Payment, refreshes authoritative state and flushes inside one transaction.
The same callback returns `02` after the first committed success without changing timestamps.
Conflicting transaction numbers are rejected. Provider-reference uniqueness and the existing
one-PAID-per-order index remain database guards, including races across different orders.
Any persistence/commit failure rolls back and returns `99` so VNPAY can retry.
Unknown order/reference returns `01`, wrong amount `04`, invalid signature `97`.
Duplicate query parameters are rejected. Only GET is implemented; no CSRF exception is added.

Valid final unsuccessful IPN closes the single attempt with `expired_at`, keeping Payment UNPAID.
Pending notifications leave it open. Return alone, cancelled orders, expired initiation links,
COD attempts and ambiguous historical records cannot set PAID. Order lifecycle/history and
financial ledgers are not mutated by callbacks.

## Build and run

Use Java 21 and the pinned Maven Wrapper:

```powershell
.\mvnw.cmd package
.\mvnw.cmd '-Ppostgres-it' '-Dtest=VnpayGatewayTest,PlaceOrderPageControllerTest,CodPaymentTest,ArchitectureTest' '-Dit.test=VnpayPaymentIT,CodPaymentIT,PlaceOrderIT,CheckoutFlowIT' verify
```

The current Render service uses **Docker**, branch `deploy/render-demo`, at base
`02a55f2c3c676e171fcdab4c2a50e23eeb3f7af1`. Its existing Dockerfile is preserved:
Java 21 JDK builder with `unzip`, `./mvnw -B -DskipTests clean package`, Java 21 JRE,
`SERVER_PORT=10000`, `UPLOAD_DIR=/app/var/uploads`, and the existing Java entrypoint.
Do not replace Docker configuration with native Build/Start commands.
Retain the service's current profiles, port, database and other environment settings.
For a manual Linux build outside Docker, use `sh ./mvnw --batch-mode --no-transfer-progress package`;
the wrapper is tracked with mode `100644` and Docker already runs `chmod +x mvnw`.
Deploy only after reviewing these local changes; no push/merge/deploy was performed by this task.

For localhost, keep credentials in the environment and use
`VNPAY_RETURN_URL=http://localhost:8080/payments/vnpay/return` with profile `local`.
Server-to-server IPN still needs a public reachable HTTPS callback for a real Sandbox transaction.
The existing Render domain is the intended demo target after deployment.

## Demo after deployment

1. Confirm Render Environment has Sandbox credentials and `VNPAY_ENABLED=true`, deploy the reviewed
   build, and register the exact public IPN URL with VNPAY Sandbox.
2. Log in as a buyer, select products from one shop, choose an address/shipping service and request a quote.
3. Choose **VNPAY Sandbox**, place the order and complete payment on the Sandbox page.
4. Official test example: NCB, card `9704198526191432198`, holder `NGUYEN VAN A`, issue `07/15`, OTP `123456`.
   These are public Sandbox test data, not merchant credentials.
5. Return shows waiting until a verified IPN commits. Click **Kiểm tra lại kết quả**; PAID is shown
   only after persisted confirmation. The buyer order details also display the recorded payment.
6. Reload Return or let VNPAY retry IPN: the same Payment remains recorded once. An altered signature
   or amount must not mark it paid. Verify COD still works by placing a separate COD order.

If waiting persists, check the registered IPN URL and VNPAY's delivery result. Do not make Return mark
the order paid to work around missing IPN. `99` on a cancelled order requires reconciliation rather
than accepting a new payment against released stock.

## Scope and limits

One Sandbox attempt per checkout, valid for 15 minutes. No automatic retry/new attempt, scheduled
order expiry or inventory release, VNPAY refund, settlement, production payments, QA-02 or QA-03 work.
Failed/unpaid orders use existing cancellation behavior. This MVP proves checkout-to-payment;
existing fulfillment checks that require COD are unchanged, so ONLINE shipping/delivery is not
claimed as supported end to end by this change.

Official references:
[PAY 2.1.0 / IPN](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html),
[Sandbox demo/test cards](https://sandbox.vnpayment.vn/apis/vnpay-demo/).

## Changed files

Configuration and handoff:

- `.env.example`
- `README.md`
- `docs/PAY-03-vnpay-sandbox.md`
- `src/main/resources/application.yml`
- `src/main/java/com/uteexpress/payment/config/VnpayConfiguration.java`
- `src/main/java/com/uteexpress/payment/config/VnpayProperties.java`

Payment implementation:

- `src/main/java/com/uteexpress/payment/service/VnpayGateway.java`
- `src/main/java/com/uteexpress/payment/service/VnpayPaymentService.java`
- `src/main/java/com/uteexpress/payment/controller/VnpayController.java`
- `src/main/java/com/uteexpress/payment/dto/VnpayIpnResponse.java`
- `src/main/java/com/uteexpress/payment/dto/VnpayReturnView.java`
- `src/main/java/com/uteexpress/payment/entity/Payment.java`
- `src/main/java/com/uteexpress/payment/repository/PaymentRepository.java`
- `src/main/java/com/uteexpress/security/SecurityConfig.java`

Checkout and UI:

- `src/main/java/com/uteexpress/checkout/controller/CheckoutPageController.java`
- `src/main/java/com/uteexpress/order/controller/PlaceOrderPageController.java`
- `src/main/java/com/uteexpress/order/service/OrderPlacementService.java`
- `src/main/resources/templates/checkout/quote.html`
- `src/main/resources/templates/payment/vnpay-result.html`

Tests:

- `src/test/java/com/uteexpress/payment/VnpayGatewayTest.java`
- `src/test/java/com/uteexpress/payment/VnpayPaymentIT.java`
- `src/test/java/com/uteexpress/order/controller/PlaceOrderPageControllerTest.java`

Pre-existing QA-02 changes and untracked files in this checkout were left untouched.

## Verification

Verified on the uncommitted working tree based on commit
`d1cfaeaee2a5bb8afa4edab29efbf050a209620e`, Java 21.0.12.1, Maven Wrapper 3.9.11.

- **PASS** — `.\mvnw.cmd package`: 691 Surefire tests, 0 failures/errors/skips, exit 0.
  Executable artifact: `target/uteexpress-0.1.0-SNAPSHOT.jar`.
- **PASS** — the focused `-Ppostgres-it ... verify` command above: 30 focused Surefire tests
  plus 127 PostgreSQL integration tests, 0 failures/errors/skips, exit 0.
  Integration scope: `VnpayPaymentIT` 23, `CodPaymentIT` 47, `PlaceOrderIT` 56, `CheckoutFlowIT` 1.
  The 30 focused Surefire tests are included in the later 691 count, not additional unique tests.
- **PASS** — `git diff --check`, exit 0.
- **FAIL remaining** — none in the final commands. Earlier attempts hit sandbox Docker access and
  test-fixture issues (a nonexistent CartService method, reused dummy provider references, and
  direct-versus-queued PostgreSQL lock expectations); these were corrected before the passing runs.
- **NOT RUN** — the full PostgreSQL suite, QA-02, QA-03, production payments and VNPAY refunds.
- **NOT RUN** — real VNPAY browser payment / VNPAY-to-Render IPN. The local terminal has no merchant
  credentials; no Render settings were accessed or changed and nothing was deployed.

Logs: `.work/pay03-package.log` and `.work/pay03-postgres.log`.
Warnings remain for Mockito dynamic agent loading, the existing Spring Security provider setup,
and existing unchecked test code. They did not fail either final command.

Covered checks include public callbacks with no authentication/CSRF token, signature tampering,
amount/merchant/reference mismatch, invalid transaction/status/date, cancelled orders, persisted
amount drift, failed/pending callbacks, delayed valid IPN, duplicate query parameters, conflicting
provider references, buyer ownership, expired initiation links, actual checkout and replay,
server-rendered checkout/result pages, and concurrent duplicate IPNs. The concurrency test verifies
both workers' actual PostgreSQL backend PIDs and blocking chains to the Order-lock holder before
release, then asserts one `00`, one `02`, one committed PAID row and unchanged Order facts.

## Render deployment preparation

The evidence above records the initial implementation. The deployment candidate is prepared
separately in `.work/pay03-render-ready`, local branch `feature/pay-03-render-ready`, on
`02a55f2c3c676e171fcdab4c2a50e23eeb3f7af1` from `origin/deploy/render-demo`.
Only the 22 listed PAY-03 files are included; the original checkout and its QA-02 files/index
are preserved. Existing Dockerfile and `.dockerignore` are unchanged. No migrations are added.

Confirmed by the service owner: `uteexpress-web`, ID `srv-dbhq6m6c6qcc7382kdgg`, repository
`hphuc032/Logistic-UTEExpress`, branch `deploy/render-demo`, runtime Docker, Auto-Deploy Off,
and no separate Build/Start commands. GitHub independently matches branch HEAD `02a55f2`.
Remote Environment variable presence/values have **not** been inspected; no secret was requested.

Compared with `origin/develop` at `b8233595833cf6a3ff8901af8266e7678119d4b2`, the deployed branch
has 3 unique commits and develop has 8. Those include deploy-specific Docker setup and newer
promotion work on develop. Order/Payment/Checkout source, checkout templates and application.yml
are identical between those two bases. The candidate uses the deployed branch as its parent;
it does not merge develop, import unrelated features or remove existing deployed features.

Final candidate validation:

- **PASS** — `mvnw.cmd -Ppostgres-it -Dit.test=VnpayPaymentIT,CodPaymentIT,PlaceOrderIT,CheckoutFlowIT verify`:
  720 Surefire + 128 PostgreSQL tests; zero failures/errors/skips; exit 0.
  VNPAY integration now includes COD checkout with VNPAY enabled (24 VNPAY cases).
- **PASS** — `docker build --tag uteexpress-pay03-render:local .`, exit 0, using the preserved Dockerfile.
  This is a local image build only; no registry push or Render deployment.
- **PASS** — `git diff --check`; the exact stage allowlist is the 22 files listed above.
- **NOT RUN** — production credentials, real Sandbox payment/IPN and the full PostgreSQL suite.
- **Live baseline issue** — before any PAY-03 push/deploy, public `/actuator/health` returned HTTP 503,
  `status: DOWN`. Its underlying failing component is unknown because details are hidden. Check
  current Render Logs and Environment (DB/mail/readiness as applicable) before the demo.

Logs are outside the commit under `.work/pay03-render-preflight/ready-verify.log` and
`.work/pay03-render-preflight/docker-build.log` in the original workspace.

After explicit authorization, push the prepared commit SHA directly as a **normal fast-forward**
update to `refs/heads/deploy/render-demo`; never force-push. Its parent is the current remote branch
HEAD. If the remote has advanced, stop and revalidate instead of overwriting it. Auto-Deploy is Off.
Then use Render **Manual Deploy -> Deploy a specific commit** and select that same SHA, or deploy
the latest branch commit only after confirming it is the prepared SHA. There is no merge step.

Before deployment, ensure Sandbox credentials exist, `VNPAY_ENABLED=true`, and the exact Return
URL shown above; save Environment changes with **Save only** until deployment is authorized.
Do not overwrite existing DB/JWT/OTP/mail settings. Missing required VNPAY configuration with the
flag enabled prevents startup and therefore affects COD too. Preserve the existing service's port
and profile settings. Record `02a55f2` as the pre-PAY-03 rollback commit.

After deploy, verify health, place one COD order, then pay a separate Sandbox order. Register the
exact IPN URL with VNPAY Sandbox. Confirm real IPN returns `RspCode=00`, Payment has one PAID row
with matching amount/provider reference, and a repeated notification returns `02` without another
payment. Return alone or a Sandbox success screen does not establish end-to-end acceptance.
