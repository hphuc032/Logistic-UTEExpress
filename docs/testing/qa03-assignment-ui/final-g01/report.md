# PR75 final G01 technical handoff

Original Master Plan was re-read (SHA25618523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a). HP priority is75 before69. **Ready for TD/HP decision on technical evidence; G01 human acceptance PENDING.** No automatic approval/merge/QA03 completion.

## Identity and scope

Accepted develop6a26370 includes merged70/74.75 was already synchronized/mergeable when checked, so no redundant sync. Exact fresh executed source **8a96ec06d15012f233161465b937898cec4ccff3**, treeb55342b10810463dc0d73f164b00c721f342b587. New source delta from3ae2c82 is only two PostgreSQL concurrency tests. All production/CI inputs match3ae2c82; fresh results below are nevertheless executed on8a96ec0 rather than relabeled historical results. Publication docs after8a96ec0 are not a new test run. PR75 remains separate/unmerged; no finalG02 run here.

## Review

ADMIN uses/admin, MANAGER uses/manager per SecurityConfig76-78 and existing service PreAuthorize guards. They are global Ops roles, not Vendor shop-ownership roles. Buyer/Vendor/Shipper cannot access Ops actions; current Shipper read ownership is rechecked after reassignment. UI is advisory: existing server assignment/reassignment services remain authority for readyCONFIRMED, originalOrder/Shipment version, active Shipper and locking. UI posts masked CSRF through same-origin fetch, no token URL/log, no amount/status/owner from browser. Errors are fixed safe text; pending submissions are not retried automatically. SecurityConfig, Shipping writer/lock protocol, Order/Payment/Refund, inventory/voucher rules and migrations match accepted develop. Only governed read projection/controller/DTO plus HTML/JS differ in production.

## Fresh verification

709unit/architecture +**313PostgreSQL in10targeted classes** PASS,0failures/errors/skips/flaky records, Maven exit0/no timeout. This is not full PostgreSQL. Ten classes include Assignment, ShipperFulfillment, RTS, Review, NotificationSocket/Recovery, OpsCancellation, AdminRevenue, ManualCodRefund and CancelledPaidRefund. All87XML hashes and Maven log were rechecked.48Node and17tooling PASS;8assignment client tests included in both local suite and actual CI step. No count added from historical2059/2124 or package-time reruns.

Two new AssignmentIT cases place ADMIN/MANAGER requests behind an externally held PostgreSQL Order lock, observe both waiting in pg_stat_activity, release it, and assert one successful assignment/reassignment plus oneCONFLICT. Assignment stays unique; reassignment version increments once; history/audit one per successful mutation. Order, payments/refunds, products/voucher usage/history/notifications remain unchanged. No production bug fix or assertion weakening was required.

Fresh Chrome155 headless **24focused UI cases PASS**: eight groups at375/768/1440. Assignment/reassignment; Manager flow; missingCSRF403/pending duplicate emits onePOST/cached serverduplicate409/nonOps403; simultaneous Admin+Manager browser requests201/409 with one persisted audit/history; failure/retry exhausted; second-attempt exactCOD; trusted RTS; keyboard skip-link/menu/focus/table-scroll. Actor/routes/statuses/timestamps/JAR/dataset/screenshot hashes and owned cleanup recorded. A separate public OTP read observation confirms10minute copy. Tool-driven keyboard and Playwright are AUTOMATED, not human actions. Four representative screenshots were visually inspected by the agent; no every-pixel/human visual audit claim.

## Findings and decisions

No Critical/High production blocker found in reviewed/tested scope. Missing direct race coverage is now addressed by two tests; CI coverage finding is resolved only after exact publication-head CI and actual48Node log are verified. OTPcopy remainsOPEN/Low, HP-owned: verify-otp.html42 says10minutes, application.yml77 isPT5M and OtpProperties16-17 caps5minutes; persisted expiry uses properties.ttl at EmailVerificationService127. Minimal proposed text is “Mã có hiệu lực tối đa5phút.” See proposal/patch text, not applied to HP source.

Genuine-human checklist covers all required assignment/reassignment, keyboard/focus, role/ownership/CSRF, transitions and responsive conditions at3widths; observer/signature and rows remain NOTRUN/PENDING. Physical device/IME/screen-reader not inferred from automation. TD must confirm latest-head Order/Payment/Refund compatibility; HP decides OTP/humanG01 acceptance and merge readiness under protection. QA03 notCOMPLETED; no approve/merge/forcepush and no finalG02 on unmerged75.

CI is checked separately on the final publication HEAD. The PR70-only PG workflow is gated/skipped on75; current313PG results are local source-bound verification, not GitHub PostgreSQL execution. Raw private logs/XML/credentials are not published; hashes/structured receipts are public. Older packets and failures remain unchanged.
