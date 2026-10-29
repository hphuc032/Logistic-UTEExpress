# PROMO-03 current acceptance matrix

All runtime PASS below is local execution on15fb1dd, not independent owner approval. Original packet preserved in history/.

| Contract | Actual evidence | Status |
|---|---|---|
|ADMIN list/create/update/disable PLATFORM|PlatformVoucherManagementIT55;VoucherCheckoutIT59|PASS — owner review pending|
|ADMIN Product management/role denial/forgery/validation/version/target eligibility|AdminProductPromotionIT41|PASS — owner review pending|
|Audit failure atomic rollback|AdminProductPromotionIT;PlatformVoucherManagementIT|PASS — owner review pending|
|Current actor/Shop freshness after observed lock waits|PlatformVoucherManagementIT;AdminProductPromotionIT|PASS — owner review pending|
|Product sentinel Admin/Vendor scheduling and checkout commit/rollback/snapshot|AdminProductPromotionIT;ProductPromotionIT59|PASS — owner review pending|
|Used voucher REDEEMED/RELEASED rule and cancellation/refund compatibility|PlatformVoucherManagementIT;VoucherCheckoutIT;ManualCodRefundIT62;CancelledPaidRefundIT61|PASS — owner review pending|
|Vendor SHOP/schedule compatibility|VendorVoucherIT73;ProductPromotionIT59|PASS — owner review pending|
|Full configured Java/architecture/PostgreSQL/package|2226tests,0failure/error/skip;clean verify exit0|PASS — owner review pending|
|JS regression|48/48existing tests PASS|PASS — owner review pending|
|Browser mobile375/tablet768/desktop1440|11cases each=33PASS;browser/|PASS — owner review pending|
|Confirmation Cancel/noPOST/noDBchange, CSRF redirect/flash, focus and containment|Chrome +PostgreSQL matched evidence|PASS — owner review pending|

|Final-source TD/QĐ review/formal GitHub approval|Earlier opinions cover67c7d54only|PENDING|
|HP merge direction|Direct human completion/merge instruction|AUTHORIZED; protection not bypassed|
|Manual independent runtime/signature|Not executed in this task|NOT RUN|
|QA02/03/Q70/release|No status mutation|UNCHANGED|
