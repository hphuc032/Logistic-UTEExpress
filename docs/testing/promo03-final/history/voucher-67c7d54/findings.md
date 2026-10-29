# PROMO-03 findings and decisions

| ID | Type | Status | Evidence / action | Owner gate |
|---|---|---|---|---|
| PROMO03-C01 | Required contract slice | OPEN / BLOCKED | Plan PROMO-03 has both promotions and vouchers. HP requests Admin product-promotion expansion after TD/QĐ boundary agreement. Existing Vendor-only writer cannot be widened implicitly; proposed decisions in owner-handoff.md | TD business + QĐ adapter agreement, HP boundary acceptance; then separate implementation/testing |
| PROMO03-D01 | Plan variance | HP DECISION RECORDED, implementation awaiting review | Manager limits in Plan replaced by explicit ADMIN-only management; MANAGER DENY ALL; no ADMIN-06 exception inheritance | TD/QĐ verify enforcement, HP final application acceptance |
| PROMO03-D02 | Plan variance | HP DECISION RECORDED, implementation awaiting review | I7 used-voucher wording versus PROMO-01 code reconciled by HP: preserve current historical mutability, including RELEASED usage | TD validates used-rule and snapshot evidence |
| PROMO03-R01 | Review gate | PENDING | PLATFORM implementation and evidence are HP-authored proposal, not independent TD/QĐ approval | TD service review; QĐ adapter/security/audit review; HP final acceptance |
| PROMO03-E01 | Evidence limitation | NOT RUN | No new independent human/browser run, production deployment, live gateway/SMTP/SCA check claimed | QĐ can request interactive evidence; unrelated QA/release gates stay open |

No historical finding is closed by this branch. No claim of vulnerability-free software is made. No old QA-02/03, FR-10 Catalog finding or Q70-S01 disposition changes. PLATFORM writes reuse existing entity/checkout rules and preserve historical rows; final actual test results are in test-results.json and report.md.

## Preserved failed attempt PROMO03-E02

Initial full run on `61b55d4` ended with 1 PostgreSQL test error, 0 assertion failures: the synthetic lock-holder fixture reused Admin as buyer and inserted the buyer FK after Voucher, inventing an Account/Voucher inversion absent from actual checkout. Fixed in `cd3651f` using a distinct USER buyer and buyer-first holder lock. Production lock/authorization code is unchanged. The original failed receipt/log/JUnit reports are retained locally and hashed in test-results.json. Focused recheck and a fresh full configured suite passed on the corrected source; only that final clean run is the acceptance regression. Status: RESOLVED — TEST FIXTURE; not a production vulnerability claim.
