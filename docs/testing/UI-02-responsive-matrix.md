# UI-02 route / responsive matrix

The generated, source-bound route rows and concrete image links are published
below after final execution. [Browser cases](ui02-responsive/browser-cases.json)
retain baseline, intermediate probe, pre-sync and final observations separately;
the authoritative final rows are selected by exact source and phase, not filename
alone. [Interactions](ui02-responsive/interactions.json) and
[HTTP controls](ui02-responsive/http-results.json) are separate from render checks.

Each PASS below covers real page render plus document containment at that width;
it is not a claim of every business action, complete accessibility conformance or
native-dialog acceptance. `Ops` includes existing ADMIN/MANAGER order/shipping
configuration and actual SHIPPER assignment/fulfillment views. No OPS role exists.

<!-- GENERATED ROUTE MATRIX -->

Final source `1f375fa01882f09755e6a107e0e13c79f427f671`: **240/240 render/containment observations PASS**, 80 actor/route rows ×3 viewports. Of these219 cover the five actor groups;21 are anonymous/public. HTTP companion:73 authenticated rows200 +9 separate security controls PASS. No sums with old-source probes.

| Actor / real role | Route | 375×812 | 768×1024 | 1440×900 | HTTP / evidence |
|---|---|---|---|---|---|
| buyer | `/` | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/products` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/products/1` | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_products_1-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_products_1-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_products_1-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/categories/ui02-category` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/shops` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/shops/ui02-shop` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/user/cart/view` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/user/checkout/view` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/orders` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/orders/1` | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_orders_1-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_orders_1-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-buyer-_orders_1-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/user/profile` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/user/addresses` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/user/favorites` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/user/recently-viewed` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/notifications` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/products/1/reviews` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/orders/1/return` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| buyer | `/order-items/1/review` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/dashboard` | [PASS](ui02-responsive/screenshots/post-sync-final-vendor-_vendor_dashboard-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-vendor-_vendor_dashboard-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-vendor-_vendor_dashboard-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/products` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/products/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/products/1/edit` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/orders` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/orders/1` | [PASS](ui02-responsive/screenshots/post-sync-final-vendor-_vendor_orders_1-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-vendor-_vendor_orders_1-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-vendor-_vendor_orders_1-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/vouchers` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/vouchers/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/products/1/promotions` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/vendor/products/1/promotions/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/user/shop` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| vendor | `/notifications` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/dashboard` | [PASS](ui02-responsive/screenshots/post-sync-final-manager-_manager_dashboard-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-manager-_manager_dashboard-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-manager-_manager_dashboard-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/accounts` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/accounts/1` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/categories` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/categories/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/categories/1/edit` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/products` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/shops/moderation` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/orders` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/orders/1` | [PASS](ui02-responsive/screenshots/post-sync-final-manager-_manager_orders_1-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-manager-_manager_orders_1-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-manager-_manager_orders_1-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/shipping/providers` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/shipping/providers/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/shipping/providers/1/edit` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/shipping/rates` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/shipping/rates/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/shipping/rates/1/edit` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/commissions` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| manager | `/manager/shippers` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/dashboard` | [PASS](ui02-responsive/screenshots/post-sync-final-admin-_admin_dashboard-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-admin-_admin_dashboard-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-admin-_admin_dashboard-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/accounts` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/accounts/1` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/categories` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/categories/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/categories/1/edit` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/products` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shops/moderation` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/orders` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/orders/1` | [PASS](ui02-responsive/screenshots/post-sync-final-admin-_admin_orders_1-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-admin-_admin_orders_1-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-admin-_admin_orders_1-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shipping/providers` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shipping/providers/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shipping/providers/1/edit` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shipping/rates` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shipping/rates/new` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shipping/rates/1/edit` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/commissions` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shippers` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shops` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/shops/1` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/audit-logs` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| admin | `/admin/dashboard/revenue` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| shipper | `/shipper/dashboard` | [PASS](ui02-responsive/screenshots/post-sync-final-shipper-_shipper_dashboard-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-shipper-_shipper_dashboard-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-shipper-_shipper_dashboard-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| shipper | `/shipper/shipments/view` | PASS (DOM) | PASS (DOM) | PASS (DOM) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| shipper | `/shipper/shipments/1/view` | [PASS](ui02-responsive/screenshots/post-sync-final-shipper-_shipper_shipments_1_view-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-shipper-_shipper_shipments_1_view-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-shipper-_shipper_shipments_1_view-1440.jpg) | 200 companion HTTP; [case records](ui02-responsive/browser-cases.json) |
| anonymous | `/login` | [PASS](ui02-responsive/screenshots/post-sync-final-anonymous-_login-375.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-anonymous-_login-768.jpg) | [PASS](ui02-responsive/screenshots/post-sync-final-anonymous-_login-1440.jpg) | Browser render; public/auth; [case records](ui02-responsive/browser-cases.json) |
| anonymous | `/register` | PASS (DOM) | PASS (DOM) | PASS (DOM) | Browser render; public/auth; [case records](ui02-responsive/browser-cases.json) |
| anonymous | `/verify-otp` | PASS (DOM) | PASS (DOM) | PASS (DOM) | Browser render; public/auth; [case records](ui02-responsive/browser-cases.json) |
| anonymous | `/forgot-password` | PASS (DOM) | PASS (DOM) | PASS (DOM) | Browser render; public/auth; [case records](ui02-responsive/browser-cases.json) |
| anonymous | `/reset-password` | PASS (DOM) | PASS (DOM) | PASS (DOM) | Browser render; public/auth; [case records](ui02-responsive/browser-cases.json) |
| anonymous | `/products` | PASS (DOM) | PASS (DOM) | PASS (DOM) | Browser render; public/auth; [case records](ui02-responsive/browser-cases.json) |
| anonymous | `/products/1` | PASS (DOM) | PASS (DOM) | PASS (DOM) | Browser render; public/auth; [case records](ui02-responsive/browser-cases.json) |

Representative route parameters are fixture IDs, not hardcoded production IDs. Full controller inventory includes JSON-only commands and protected binary media; they are not invented HTML pages. `/orders/1/return` and `/order-items/1/review` use a genuinely DELIVERED owner order, created/advanced through owner HTTP contracts. They were GET/layout checked only; return/refund/review writes remain NOT RUN. Promotion/voucher forms were GET/layout checked; after sync a real invalid voucher POST and its three validation screenshots PASS, while successful create/edit/disable browser interactions remain NOT RUN after browser use was disabled.

Loading/disabled/error: cancellation pending/error/OS dialog interactions are BLOCKED/NOT RUN at final source; no Bootstrap modal is introduced. Anonymous empty/review, own populated order, staff tables and actual form errors are separately observable; this matrix does not imply every possible state.

### Baseline / intermediate failures

| Phase/source | Actor/route | Viewport | Observation | Evidence |
|---|---|---|---|---|
| before | anonymous `/products/1` | 375 | document1338px | [image](ui02-responsive/screenshots/before-anonymous-_products_1-375.jpg) |
| before | admin `/admin/orders/1` | 375 | document818px | [image](ui02-responsive/screenshots/before-admin-_admin_orders_1-375.jpg) |
| before | admin `/admin/orders/1` | 768 | document834px | [image](ui02-responsive/screenshots/before-admin-_admin_orders_1-768.jpg) |
| probe | buyer `/products/1` | 375 | document372px | [image](ui02-responsive/screenshots/probe-buyer-_products_1-375.jpg) |
| probe | manager `/manager/products` | 375 | document531px | [DOM record](ui02-responsive/browser-cases.json); no screenshot saved for this probe |
| probe | manager `/manager/shops/moderation` | 375 | document440px | [DOM record](ui02-responsive/browser-cases.json); no screenshot saved for this probe |
| probe | admin `/admin/products` | 375 | document531px | [DOM record](ui02-responsive/browser-cases.json); no screenshot saved for this probe |
| probe | admin `/admin/shops/moderation` | 375 | document440px | [DOM record](ui02-responsive/browser-cases.json); no screenshot saved for this probe |

## Additional actual routes/states inventoried but not accepted

| Actor / route or state | Status | Reason / boundary |
|---|---|---|
| Buyer `/orders/{id}/payments` HTML | NOT RUN as separate browser navigation | Existing controller redirects to owned order detail `#payment-title`; populated payment section was observed in detail. No new payment screen is invented. |
| Vendor `/vendor/vouchers/{id}/edit` | NOT RUN | No persisted voucher fixture in this UI run; successful create/edit browser testing stopped when browser use was disabled. GET new/list and invalid POST validation have separate evidence. |
| Vendor `/vendor/products/{productId}/promotions/{id}/edit` | NOT RUN | No persisted promotion fixture. New/list pages are checked; PR68's6 real-HTTP binding cases are automated integration, not browser acceptance. |
| Vendor `/vendor/orders/{id}/return` and return decision/receipt states | NOT RUN | No requested-return fixture was created. Buyer eligible return form GET is covered; return/refund writes were not performed for UI-02. |
| Ops cancellation, failed-delivery/retry/return-to-sender/refund states | NOT RUN as exhaustive browser acceptance | Core order/shipment views and actual happy delivery forms were exercised; exception lifecycle is covered by existing Java suites at their stated source, not a new complete browser run. |
| Native confirm, pending/error/disabled cancellation states | BLOCKED / NOT RUN at final source | Browser tool could not establish physical confirmation semantics; browser use was subsequently disabled. Historical PR67 manual evidence is not current-source proof. |
| Manager/Admin commission and shipper registry unwrapped tables | Observed / LOW consistency follow-up | Existing native table/control presentation remains; final populated rows rendered without page overflow. No policy/column/financial change was made. |
| Responsive physical device/IME/OS keyboard and audible screen reader | NOT RUN | Explicit CSS viewport emulation is narrower than hardware/accessibility acceptance. |

API-only assignment/reassignment, lifecycle and refund commands are owner REST contracts, not missing HTML routes to fabricate. Protected media endpoints require owned/visible binary fixtures and are not counted as HTML acceptance rows. QĐ may add independent evidence for these states; UI-02 author evidence does not close their task owners' gates.


## Supplemental browser/manual verification — 2026-10-29

The original execution history above remains intact. A new
[follow-up packet](ui02-responsive/followup/README.md) on source
`1124a4908d886b4edfa872839ab90208f38e9ff1` supersedes the historical NOT RUN
status **only** for the cases below; executable objects match runtime1f375fa.

| Gate | New evidence and precise scope |
|---|---|
| Native dismissal | HP manual Escape375, Cancel768, Tab/Shift+Tab then Escape1440. Each returns focus to enabled cancel button. PostgreSQL order2 remains NEW/version0 with original timestamp; zero cancellation POSTs. QĐ independent verification is pending; no native-dialog-chrome screenshot or every action at every width is claimed. |
| Voucher creation | Real Vendor form with CSRF: POST302→edit GET200, success flash, owned SHOP voucher persisted. Submit375; successful edit page containment/screenshots375/768/1440. Update/disable remain NOT RUN. |
| Promotion creation | Real Vendor form with CSRF: POST302→edit GET200, success flash, product1/10%/active persisted. Submit375; successful edit page containment/screenshots375/768/1440. Update/disable remain NOT RUN. |
| Regression/scope |40 Node PASS freshly executed. No production changes; no fresh local Maven/full PostgreSQL run claimed. New publication HEAD/CI is recorded on PR74. |

Original native timeout/changed order and browser-disabled limitations are
historical, not deleted. This run used the Brave extension after browser access
was restored; native tool timeouts were completed by HP manual actions and are
labelled as such. The earlier OK action is separately documented as
CANCELLED/version1, never dismissal evidence. The original151 images/161 hashes
and240 cases remain unchanged. The supplemental packet has its own manifest.
Independent QĐ review and HP acceptance/merge are still required; UI-02 and the
QA/release tasks are not marked COMPLETED. See the packet checklist and remaining
limitations rather than interpreting old NOT RUN entries as current create-case
status or interpreting new results as exhaustive CRUD/browser acceptance.

## Review disposition — 2026-10-29

HP accepts the disclosed manual native-confirmation evidence limit for UI-02
only. Sampled actions/widths above remain exactly as executed; no independent QĐ
browser run or exhaustive matrix is added by this decision. QĐ'sb526fca review
is COMMENTED / scoped read-back PASS, not APPROVED. CI now includes app.test.cjs
with the existing suites (local40 PASS; new CI result on PR74). See
[the report](UI-02-responsive-report.md) for the decision and unchanged QA/release boundaries.
