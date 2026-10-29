# PR68 post-sync delta review / handoff to QĐ

Status: TD static review completed; QĐ independent delta review PENDING. This document is not a QĐ sign-off.

The normal two-parent merge has no conflicts and no manual conflict resolutions. Its parents are old PR HEAD `c593352492184da2a9ca46ebea4a55b802ac6416` and develop `b538dbc3af17ff80802fa33eea187705c40059fe`. Exact source `8705e8a865736387c96007aee7eb813a58dd1911`, tree `3cba80a0ab92b90e7a82e9b4726acdb9bc0f328c`; ahead7/behind0 against the pinned develop. The source differs from old HEAD only by the 26 upstream QA-01 files below. The PR diff against develop remains the original 11 files. No extra application patch was introduced.

## Reviewed compatibility boundaries

- Controller binding: both owned controllers retain ExtendedServletRequestDataBinder headerPredicate(false), existing business allowlists, and path-derived identity. Promotion disable accepts only form version; client identifiers/unexpected fields are still rejected. No binder or form DTO is changed by the merge.
- Validation: @Valid, suppressed-field checks, required/nonnegative disable version, optimistic version checks, discount/time/overlap constraints remain. VendorProductPromotionBindingTest freshly passes all11 MVC cases; it uses a mocked service and is not persistence/CSRF proof.
- Authorization and ownership: /vendor/** still requires VENDOR; ProductPromotionManagementService and VendorVoucherService still derive the actor from CurrentAccountIdProvider and authorize active account plus approved owned shop/product under existing lock order. No client shop/product/actor identity is trusted.
- Shared security delta: SecurityConfig adds the bounded OTP request filter after CsrfCookieFilter, itself after CsrfFilter. Its matcher is limited to POST register/verify-otp/resend/forgot-password/reset-password, so promotion/voucher URLs do not consume its budget. It uses MVC-compatible parsed paths and socket remoteAddr; forwarding headers are not trusted. Filter servlet registration is disabled to avoid double counting. Existing CSRF repository, JWT restoration and vendor authorization matchers are unchanged.
- OTP delta belongs to upstream HP QA-01: maximum five-minute TTL, at most5 attempts, resend at least60 seconds; existing longer-lived records are capped at consumption without migrations. These tighter guards and test-profile PT5M are retained exactly; no source fix or relaxation was made by TD.
- Business logic: promotion/voucher pricing, overlap/exclusion constraints, quota/redemption, transaction/lock boundaries, order snapshots and refund/payment guards have no merge delta. All migrations are unchanged.

## Runtime limits and QĐ checklist

- Fresh705 unit PASS includes binding11, security/filter/OTP tests and all unit/architecture suites. Fresh Node33 and tooling17 PASS with documented environment accommodations.
- Full PostgreSQL attempt FAILED during Docker/Testcontainers setup:65 reports,73 initialization errors, zero successful integration testcases. These are not73 executed business scenarios and do not replace the old1326 PASS.
- PromotionBrowserFormsIT, Qa01CookieCsrfIT, Qa01OtpSecurityIT, VendorVoucherIT, ProductPromotionIT and other ownership/business PostgreSQL suites must run in a Docker-authorized context on this exact source. Actual HTTP/cookie/CSRF/persisted ownership remain NOT VERIFIED after sync.
- QĐ should independently review filter ordering/matcher scope versus vendor forms; OTP policy/test-profile compatibility; unchanged form/ownership/version guards; and fresh PostgreSQL results once available. Review remains PENDING until QĐ responds.
- Q70-S01 ACCEPTED RISK — LIMITED is unchanged. QA-02 stays IN PROGRESS; QA-03 is not declared complete; no finding is closed and PR68 is not approved/merged.

## Upstream files introduced by sync

- `docs/security.md`
- `docs/testing/.gitattributes`
- `docs/testing/QA-01-evidence-manifest.md`
- `docs/testing/QA-01-findings.md`
- `docs/testing/QA-01-runner-evidence.json`
- `docs/testing/QA-01-security-matrix.md`
- `docs/testing/QA-01-security-report.md`
- `scripts/security/qa01-semgrep.yml`
- `src/main/java/com/uteexpress/common/exception/ErrorCode.java`
- `src/main/java/com/uteexpress/identity/service/EmailVerificationService.java`
- `src/main/java/com/uteexpress/identity/service/OtpProperties.java`
- `src/main/java/com/uteexpress/identity/service/PasswordResetService.java`
- `src/main/java/com/uteexpress/security/OtpRequestLimitProperties.java`
- `src/main/java/com/uteexpress/security/SecurityConfig.java`
- `src/main/java/com/uteexpress/security/dto/RequestBudgetDecision.java`
- `src/main/java/com/uteexpress/security/filter/OtpRequestLimitFilter.java`
- `src/main/java/com/uteexpress/security/service/OtpRequestRateLimiter.java`
- `src/main/resources/application.yml`
- `src/test/java/com/uteexpress/identity/service/EmailVerificationServiceTest.java`
- `src/test/java/com/uteexpress/identity/service/OtpSecurityTest.java`
- `src/test/java/com/uteexpress/identity/service/PasswordResetServiceTest.java`
- `src/test/java/com/uteexpress/security/OtpRequestLimitFilterTest.java`
- `src/test/java/com/uteexpress/security/OtpRequestRateLimiterTest.java`
- `src/test/java/com/uteexpress/security/Qa01CookieCsrfIT.java`
- `src/test/java/com/uteexpress/security/Qa01OtpSecurityIT.java`
- `src/test/resources/application-test.yml`
