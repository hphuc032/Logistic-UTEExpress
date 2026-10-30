# SEC-02 — Mid-project security gate

Owner: Hoàng Phúc (HP). Reviewer: Quốc Đạt (QD).
Branch: `fix/sec-02-security-hardening`.
Base develop: `ac3203344ea367ef865ccaf94b7b3d0618dd9ec0` (INT-01 PR #35 merged; commit `679aae7fda4805aa6754921f3bec0adcdfda69e1` is an ancestor).

## Scope and verdict

Reviewed current reachable controllers, their service authorization/state boundaries, request DTOs/form binding, rendering, SQL, JWT/OTP, local image storage and existing regression evidence across identity, account, shop/catalog, cart/checkout/order, COD payment, governance, shipping configuration and engagement. ADMIN account/role governance exists on this base. No current shipper business controller or WebSocket/STOMP implementation/dependency exists.

No confirmed HIGH, MEDIUM or LOW exploitable vulnerability was found in these boundaries. All 29 current production controllers were covered by the independent baseline, supplemented by upload and persistence/DTO reviews and parent validation. This is an evidence-backed clean boundary audit, not a guarantee about deployment, all dependencies or every historical document. Repository searches alone do not establish complete source coverage. The supplemental Codex Security scan reports partial whole-repository full-read coverage and its limits explicitly.

| Classification | Observation | Action |
| --- | --- | --- |
| INFORMATIONAL | Governance invalidation and JWT rejection were previously tested separately; missing end-to-end regression evidence | Three real-JWT PostgreSQL tests exercise actual governance HTTP mutations |
| INFORMATIONAL | Invalid image validation/compensation had mostly unit or separate boundary evidence | Add avatar preservation, forged product upload and real transaction rollback tests |
| INFORMATIONAL | Future socket controls are not implemented | Specify the enforceable WS-01/WS-02 contract below; add no socket feature |
| LOW residual risk, not a validated vulnerability | No global login/registration abuse limiter; OTP has per-user cooldown/attempt limits | Deployment/team follow-up; this task does not claim volumetric abuse prevention |
| INFORMATIONAL | Product GET intentionally records bounded recent-view telemetry | Accepted passive page-view behavior; no favorite/cart/order/payment state changes |

No production fix, dependency, migration, SecurityConfig exemption or broad refactoring is justified by the audit. Changes add six focused tests and this contract/evidence report.

## Boundary matrix

Invariant: **ACCESS = ROLE + OWNERSHIP + BUSINESS STATE**. `U/V` means USER or VENDOR; `A/M` means ADMIN or MANAGER. CSRF applies to every unsafe method, including public auth POSTs. IDs listed are request data, never authorization proof. “Existing” references name actual test classes, supplemented by the full regression gate.

| Route / service | Actor | Ownership source | Client IDs | State guard | CSRF | Sensitive/server-owned fields | Upload | Finding | Evidence/fix |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `/`, `/products/**`, `/categories/**`, `/shops/**`; PublicCatalogService | Public | Public visibility SQL; image belongs to product | Product/image IDs, shop/category slug | ACTIVE product, APPROVED shop, active category; fixed ProductSort | GET; passive view telemetry | No private account/address/order data | Image read | Clean | PublicCatalogIT, ProductDiscoveryIT |
| `/register`; RegistrationService | Public | New identity resolved server-side | None; email/username | Normalized UNIQUE identity, pending verification, default USER | POST | Role/status/hash/tokenVersion excluded | No | Clean | AuthRegistrationIT |
| `/login`, `/logout`; LoginService/JWT/logout handlers | Public / authenticated | BCrypt credentials; JWT principal; DB account | Identifier | ACTIVE account, signature/issuer/audience/expiry/version; logout rotates version | POST | Signing key, password, JWT never rendered/logged | No | Clean | Auth02AuthenticationIT, CsrfCookieWebTest |
| `/verify-otp`, `/verify-otp/resend`; EmailVerificationService | Public | Persisted user + purpose/latest locked token | Identifier/code | PENDING, TTL, attempts, cooldown, consumption | POST | HMAC/pepper/internal state excluded | No | Clean | Auth03EmailVerificationIT |
| `/forgot-password`, `/reset-password`; PasswordResetService | Public | Persisted eligible user + RESET_PASSWORD token | Email/code | ACTIVE; purpose/TTL/latest/attempts; atomic consume/hash/version | POST | Generic response; no hash/account-state disclosure | No | Clean | Auth04PasswordResetIT |
| `/user/profile`, `/user/password`; ProfileService/AccountIdentityService | U/V | CurrentAccountIdProvider | None | ACTIVE; current password proof for change | POST | Only fullName/phone or password form fields; no roles/status/userId | No | Clean | User01ProfileIT |
| `/user/avatar`; ProfileService | U/V | Principal's persisted account | None; file metadata untrusted | Active owner row lock, validated image | POST | Key generated; owner/key inputs ignored | Yes | Evidence gap closed | User01ProfileIT `sec02InvalidAvatarReplacement...` |
| `/user/addresses/**`; AddressService/AddressQueryService | U/V | Principal + `findByIdAndUserId` | Address ID | Active account lock; default assigned by service | POST | Owner/default proof not browser-owned | No | Clean | User02AddressIT, CheckoutFlowIT |
| `/user/shop`, `/user/shop/register`; ShopRegistrationService | U/V | Persisted principal ID | None | One shop per owner; creates PENDING | POST | Owner/approval/moderation excluded | No | Clean | ShopRegistrationIT |
| `/vendor/products/**`; ProductService | V | Approved persisted owned shop + scoped product | Product/category ID, expectedVersion | Approved shop; category active; optimistic version | POST | Product moderation/owner/shop excluded; vendor price is catalog input, never checkout snapshot authority | No | Clean | VendorProductIT |
| `/vendor/products/{id}/images/**`; ProductService | V | Product+owned shop; image+product | Product/image IDs | Row lock; five-image cap; content validation | POST | Storage key/moderation/owner excluded | Yes | Evidence gaps closed | VendorProductIT `sec02Malformed...`, `sec02RealTransaction...` |
| `/user/cart/**`; CartService | U/V | Principal cart/item scope | Product/item ID, quantity, selection | Serialized cart; catalog/quantity limits | POST | userId/ownerId never used as authority | No | Clean | CartDatabaseIT, CartActionsIT |
| `/user/checkout/**`; CheckoutQuoteService/OrderPlacementService | U/V | Principal buyer; owned cart/address | Product/address/provider ID, service code, key | One shop; fresh quote; locks/stock; buyer-scoped idempotency; COD | POST | All money/commission/payment/status/hash calculated server-side | No | Clean | CheckoutQuoteIT, PlaceOrderIT, CheckoutFlowIT |
| `/orders/**`; BuyerOrderService | U/V | `findByIdAndBuyerId` before child reads | Order ID/page | Persisted immutable snapshots; bounded page/size | GET | Foreign address/phone/order hidden | No | Clean | BuyerOrderIT, CheckoutFlowIT |
| `/orders/{orderId}/payments`; PaymentReadService/CodPaymentService | U/V | Buyer-owned order before payment read | Order ID | Read only; COD initialization joins placement transaction | GET | No browser payment status/paid amount writes | No | Clean | CodPaymentIT |
| `/vendor/orders/**`; VendorOrderService/VendorOrderAuthority | V | Active vendor + approved owned shop + order shop scope | Order ID/version/reason | OrderLifecycleService; expected state/version; inventory/payment guards | POST | Buyer/shop/history actor/state/timestamps server-derived | No | Clean | VendorOrderIT |
| `/user/favorites/**`, `/user/recently-viewed/**`; EngagementService | U/V | Principal user_id in repository | Product ID | Current public eligibility; recent cap 30 | POST; passive detail GET as above | Foreign history/favorites hidden | No | Clean | EngagementIT |
| `/{admin|manager}/accounts/**`; AccountGovernanceService/RoleGovernanceService | A/M read; A write | Principal audit actor; role method guards | Target account/version/ManagedRole | ACTIVE/LOCKED transitions; self-lock blocked; last-admin serialized; token rotation | POST | actor/status/roles/version decisions server-owned | No | Evidence gap closed | AccountGovernanceIT, RoleGovernanceIT, SecurityHardeningIT |
| `/admin/shops/**`; ShopApprovalService | A | Principal audit actor; persisted shop owner | Shop ID/version/rejection reason | PENDING; active owner; VENDOR grant+token rotation | POST | Owner/approval state not bindable | No | Clean | ShopApprovalIT |
| `/{admin|manager}/products/**`, shop moderation; moderation services | A/M | Ops service authority; principal audit actor | Product/shop ID/version/reason | Locked expected moderation state/version | POST | No direct order status edits | No | Clean | ModerationIT |
| `/{admin|manager}/categories/**`; CategoryService | A/M | Ops authority; principal audit actor | Category ID/version | Validation/version; enable/disable | POST | Narrow configuration fields | No | Clean | CategoryIT |
| `/{admin|manager}/shipping/**`; ShippingConfigService | A/M | Ops authority; principal audit actor | Provider/rate ID/version | Validated fees/ranges/service codes and provider state | POST | No buyer-supplied quote fees | No | Clean | ShippingConfigIT |
| `/{admin|manager}/commissions`; CommissionPolicyManagementService | A/M read; A write | ADMIN method guard; principal audit actor | None | Rate 0..100; effective time validation; persisted policy | POST | Order commission snapshots immutable | No | Clean | CommissionPolicyIT |
| `/{admin|manager}/shippers`; RoleGovernanceService | A/M read | Persisted ACTIVE SHIPPER role | None | Read-only active account query | GET | No shipment assignment capability | No | Clean | RoleGovernanceIT |
| `/admin/dashboard`, `/manager/dashboard`; OpsDashboardService | Respective Ops role | URL and method authority | None | Aggregate reads | GET | No mutation | No | Clean | OpsDashboardTest, Auth02AuthenticationIT |
| `/api/v1/foundation`, `/actuator/health`, static resources | Public | Public allowlist | None | Fixed endpoints; health only exposed | GET | Safe errors; no stacktrace | No | Clean | FoundationHttpTest, UiLayoutTest, SecurityFoundationTest |
| `/ws`, `/shipper/**` business operations | Future | Not implemented | Not applicable | Future task guards required | CONNECT below | Minimal notifications | No | Contract only | Deferred WS/SHIP owners |

## DTO, CSRF, rendering and queries

Controllers bind DTOs/forms, never entities. Current form allowlists exclude server-owned identity/status/ownership fields; immutable request records contain intended inputs only. Global Jackson `fail-on-unknown-properties` rejects unknown JSON fields. INT-01 already proves checkout client price/total/commission/buyer/shop/status injection cannot become persisted authority.

CSRF remains globally enabled with CookieCsrfTokenRepository; JWT authentication is stateless with HTTP Basic/formLogin disabled. Login rotates CSRF at the authentication boundary; ordinary JWT restoration does not rotate a token on every asset request. Thymeleaf POST forms use `th:action`; the current application has no mutation AJAX client. Any future AJAX client must submit the valid server token using the configured CSRF header and keep Spring's token-masking convention consistent. No callback exemptions currently exist.

User-controlled profile/shop/product/address/order/reason/admin text uses escaped Thymeleaf output (`th:text`, `th:field` or escaped attributes). No current `th:utext`, `innerHTML` or equivalent active-content insertion was found; shared JavaScript uses `textContent`. SQL values are parameterized. Catalog sorts map to a closed ProductSort enum/fixed SQL fragments; pagination and form inputs have bounds. No generic sanitizer is introduced.

## Upload and JWT evidence

Only avatar/product-image uploads exist. Avatar: 2 MiB; product: 5 MiB and five images; both: 4096 per dimension. MIME, extension, signature, decoder format and full decode must agree. SVG/HTML/spoofed/truncated/oversized images fail. UUID keys, normalized containment and real-path checks prevent client filename path authority. Responses have fixed raster MIME and `nosniff`; upload root is not a public resource mapping. Ownership is checked before product file mutation. New product files are cleaned on rollback and deletion waits for commit. Avatar DB failure cleans the new file; malformed replacement retains the previous key/file.

JWTs validate HS256, issuer, audience, identity claims and expiry; every request reloads ACTIVE status, current roles and tokenVersion. Auth cookie is HttpOnly/SameSite=Lax; Secure defaults true and prod forces true, while local HTTP is explicitly false. Logout, password change/reset, account lock/unlock and managed role changes invalidate previous tokens. SecurityHardeningIT now proves actual Admin HTTP changes cause old JWT rejection; denied Manager and CSRF-less requests create no account/audit mutation. Last-admin protections and self-lock protection remain unchanged.

## Enforceable future WebSocket contract (QD / WS-01 / WS-02)

These are implementation acceptance requirements, not claims of existing enforcement:

1. `/ws` handshake must authenticate from the existing valid JWT cookie and persisted ACTIVE account/current tokenVersion/current roles. Reuse the HTTP identity boundary; never accept userId/recipientId or STOMP login headers as identity.
2. Require an explicit configured Origin allowlist (scheme/host/port); no `*`, suffix guesses or reflected origin. Define the absent-Origin policy explicitly; never grant it ambient browser privilege by default.
3. STOMP CONNECT must carry the valid Spring CSRF token in the configured header; a cookie alone does not satisfy CONNECT CSRF. Verify token handling against Spring's actual HTTP/STOMP masking behavior.
4. Server establishes Principal. Deliver private notifications through `/user/queue/notifications` using server-selected recipients. A client may subscribe only its own resolved user destination; reject foreign user IDs/session destinations and all unapproved topics.
5. Deny client MESSAGE/SUBSCRIBE by default. Explicitly allow only approved application destinations with message-specific role/ownership/state checks. Reject client publishing directly to broker `/queue/**`, `/topic/**` or `/user/**`; `recipientId` never establishes delivery authority.
6. On CONNECT and inbound SEND/SUBSCRIBE, revalidate expiry, ACTIVE status, tokenVersion and current roles. Rejected/revoked connections must close and remove subscriptions. Existing open subscriptions must not keep receiving privileged pushes after revocation: revalidate recipient/session authority before outbound delivery and disconnect expired/revoked sessions even while idle, using bounded expiry scheduling or equivalent. HTTP per-request JWT checks alone do not enforce a long-lived socket.
7. Notification payloads must be minimal identifiers/type/safe display text, with no address, phone, payment details, token or secret. Opening referenced resources still calls the owner-scoped HTTP/service path. Render notification text with `textContent`.
8. WS tests must cover bad/missing/revoked JWT; absent/wrong Origin; missing/wrong CONNECT CSRF; foreign subscription/recipient injection; protected broker SEND; expiry/lock/role-change during an open connection; and privacy. No notification/shipment/realtime feature is implemented in SEC-02.

## Verification

Executed on 2026-10-06 using Java 21 and Docker Desktop Linux engine 29.4.3 with PostgreSQL 17.6/Testcontainers. No PostgreSQL claim is based on persistence-disabled HTTP tests.

| Command | Actual result |
| --- | --- |
| `.\mvnw.cmd --batch-mode --no-transfer-progress clean test` | PASS: 339 tests, 0 failures/errors/skips |
| `.\mvnw.cmd --batch-mode --no-transfer-progress package` | PASS: 339 tests, 0 failures/errors/skips; executable JAR built |
| `.\mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-it clean verify` | PASS: 339 Surefire + 425 Failsafe tests = **764 distinct tests**, 0 failures/errors/skips |
| `git diff --check` | PASS before commit |

Counts come from the final XML reports: 56 Surefire test classes and 31 Failsafe integration classes. Repeated unit executions across commands are not counted as additional distinct tests. Flyway validated all 17 migrations; Hibernate validation and the existing fresh/upgrade database tests passed. AUTH-01/02/03/04, profile/address, vendor/catalog, cart/checkout, ORD-02/03, PAY-01, governance/shipping/engagement, architecture and UI/security regressions passed.

Six added integration cases passed: one invalid-avatar preservation case, two product-image tampering/real-transaction rollback cases and three governance/real-JWT cases. Final class totals: User01ProfileIT 7, VendorProductIT 9 and SecurityHardeningIT 3. Existing ID-swapping, JSON/form tampering, missing-CSRF, traversal and last-admin tests are reused rather than duplicated.

Non-blocking test-process observation: after all integration cases completed, cached contexts emitted Hikari closed-connection warnings during container shutdown and the runner reported `Surefire is going to kill self fork JVM` after its 30-second exit grace period. Maven exited 0 with BUILD SUCCESS; Failsafe summary reports timeout=false, 425 completed and zero failures/errors/skips/flakes. This is recorded as test shutdown follow-up, not hidden or counted as a production security defect.

## Limits / deferred scope

No deployment assessment, dependency CVE scan or volumetric abuse test was performed. TLS, secret rotation, UPLOAD_DIR outside public serving paths, storage ACLs, database grants/TLS, SMTP controls and external rate limits require deployment review. Fixed keys in test configuration are test-only. File cleanup is best-effort; crashes may leave orphan files requiring operational reconciliation. WebP fails closed if no decoder exists.

Supporting schema observation for TD/QD: ORD-01's documented deferred `orders.shop_id` and `order_items.product_id` foreign keys have not been added by the current migrations; the commission-policy foreign key has been added. Current placement resolves these references on the server and no attacker-controlled bypass was found. No migration is added or changed in SEC-02; completion of the deferred FKs remains an ORD/DB owner follow-up.

Deferred: WS-01/WS-02 implementation, SHIP-01/02, review/video uploads, notifications, return/refund, VNPAY, refresh tokens/Redis and unrelated features. This PR is never merged by the agent; QD reviews the mid-project security gate.
