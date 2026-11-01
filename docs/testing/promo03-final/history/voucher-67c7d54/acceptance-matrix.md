# PROMO-03 acceptance matrix

All PASS below means local source-bound execution, not independent TD/QĐ review. Tested SHA and exact class/report hashes: test-results.json. No browser PASS is inferred from MockMvc.

| Contract | Evidence | Actual | Owner status |
|---|---|---|---|
| ADMIN list/detail/new/edit, PLATFORM only | PlatformVoucherManagementIT.adminListDetailNewAndEditRenderOnlyPlatformWithCsrfAndVersion | PASS | TD/QĐ review pending |
| Create server scope/null shop/actor/canonical code/timezone/audit | createCanonicalizesAndDerivesScopeShopAndActorWithAtomicAudit | PASS | Review pending |
| Cross-role route and direct service denial | everyRouteRejectsOtherRoles; methodSecurityRejectsDirectServiceInvocation | PASS | Review pending |
| Persisted account status/role revocation, including stale cached JPA user after lock wait | stalePrincipalCannotAuthorizePersistedInactiveOrRevokedAdmin; accountLockWaitRechecksRevocationCommittedBeforeManagement | PASS | QĐ review pending |
| Missing/invalid CSRF, forged scope/shop/creator/usage/amount/id | missingAndInvalidCsrfRejectEveryMutation; forgedFieldsCannotCreateOrUpdateOrDisable | PASS | QĐ review pending |
| Invalid window/percentage/fixed money/quotas; duplicate canonical SHOP code | invalidDefinitionDoesNotPersist; duplicateAcrossShopScopeAndInvalidFixedAmountRejected | PASS | TD review pending |
| Unused update, stale update/disable, idempotent current-version disable | unusedUpdateAndDisableRequireFreshVersionsAndAuditOnlyCommittedChanges | PASS | Review pending |
| Used and RELEASED-history restrictions; immutable Orders/usages | usedRulesReuseExistingModelAndPreserveHistoricalRows | PASS | HP rule decision recorded; TD verification pending |
| Cross-scope ID denial | shopResourceCannotBeManagedThroughPlatform | PASS | Review pending |
| Audit failure rolls back all three management writes | auditFailureRollsBackCreateUpdateAndDisable (real PostgreSQL trigger) | PASS | QĐ review pending |
| Voucher lock wait refresh sees newly committed usage | managementSeesUsageCommittedByCheckoutWhileWaitingForVoucherLock | PASS | TD review pending |
| Real checkout transaction consumes quota while Admin update waits | VoucherCheckoutIT.realCheckoutQuotaTransactionSerializesAdminUpdateAndExposesNewUsageAfterWait | PASS | TD review pending |
| Admin-created PLATFORM quote/place NEW, authoritative discount/commission, disable future use; immutable snapshots and cancellation release | VoucherCheckoutIT.adminCreatedPlatformVoucherUsesExistingCheckoutAndDisablePreservesSnapshotsAndCancellation | PASS | TD review pending |
| Real JWT cookie + rendered CSRF create/update/disable | realJwtCookieAndRenderedCsrfTokensAuthorizeCreateEditAndDisableForms | PASS (HTTP/MockMvc) | QĐ review pending |
| Existing SHOP and product pricing, Order/Payment/Refund ledger, cancellation release-once and no post-delivery quota restoration | Full configured PostgreSQL suite, notably VendorVoucherIT, ProductPromotionIT, VoucherCheckoutIT, ManualCodRefundIT, CancelledPaidRefundIT | PASS | Independent owner review pending |
| Architecture/unit/JS regression; package | clean verify and four Node suites | PASS; counts in test-results.json | Independent review pending |
| Admin product-promotion management | Plan and HP decision; proposed boundary in owner-handoff.md | BLOCKED — exact TD/QĐ boundary agreement required, NOT IMPLEMENTED | TD/QĐ/HP |
| Interactive browser/human UI review | None in this task | NOT RUN | QĐ determines review evidence |
| Final acceptance / PROMO-03 COMPLETED | No final owner approvals | NOT ACCEPTED / NOT COMPLETED | HP |
