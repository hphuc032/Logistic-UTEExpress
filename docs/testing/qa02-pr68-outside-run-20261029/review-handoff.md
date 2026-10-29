# PR68 outside-PowerShell evidence handoff for QĐ / HP

Source at collection `8705e8a865736387c96007aee7eb813a58dd1911`, tree `3cba80a0ab92b90e7a82e9b4726acdb9bc0f328c`, clean; source SHA and PR branch held unchanged. Independent compilation from a clean detached copy of the immutable commit matches all683 main/runtime and220 test/resource files, and683 JAR runtime entries byte-for-byte. This establishes retrospective executable/resource equivalence; it is not an invented original source guard.

## Fresh execution verified from original artifacts

External PowerShell raw Maven log plus141 XML suites verify705 unit +1352 PostgreSQL =2057 PASS, zero failures/errors/skips/flakes. BUILD SUCCESS, duration08:02min; finished2026-10-29T11:58:43+07:00 (04:58:43Z). Failsafe timeout=false/completed1352; all141 log suite summaries match XML names/counts. Java21.0.12.1, Docker Server29.8.0, PostgreSQL17.6 and Testcontainers2.0.5 are confirmed in raw runtime records. Maven enforcer's Java/Maven rules pass; wrapper is pinned to3.9.11.

The successful runtime belongs to user PowerShell outside Codex sandbox. Codex collected/hashed artifacts and ran only a separate reference `test-compile`; it did not run PostgreSQL or gain Docker permission. Prior sandbox FAIL remains immutable.

## Explicit provenance limits / owner verification request

- Exit0 is the user's direct report, corroborated by BUILD SUCCESS and zero-error XML. No independently captured original `$LASTEXITCODE`/Popen receipt was found; no observed process exit is invented.
- Exact external launch argv/flags and precise start timestamp are NOT CAPTURED. The log shows clean/test/package/integration-test/verify phases. Its08:02 duration is not substituted for an exact process-start record.
- Original external pre/post Git snapshots are NOT CAPTURED. Current clean SHA/tree, unchanged HEAD reflog, path-consistent XML and independent byte-identical compilation strongly corroborate executable identity; raw XML paths alone are not used as SHA proof.
- Therefore classify this as **PASS_ARTIFACTS_VERIFIED_WITH_PROVENANCE_LIMITS**, not an unqualified contemporaneously guarded exact-head run. HP/QĐ must evaluate the retrospective proof or use QĐ's separately guarded independent run. No invented receipt or rerun is claimed.

## Verified narrow HTTP/security/business coverage

| Suite | Fresh tests | Failure/error/skip |
|---|---:|---|
| `com.uteexpress.integration.PromotionBrowserFormsIT` | 6 | 0/0/0 |
| `com.uteexpress.promotion.VendorVoucherIT` | 73 | 0/0/0 |
| `com.uteexpress.promotion.ProductPromotionIT` | 59 | 0/0/0 |
| `com.uteexpress.security.Qa01CookieCsrfIT` | 15 | 0/0/0 |
| `com.uteexpress.security.Qa01OtpSecurityIT` | 11 | 0/0/0 |
| `com.uteexpress.catalog.controller.VendorProductPromotionBindingTest` | 11 | 0/0/0 |

PromotionBrowserFormsIT is the real HTTP/cookie/CSRF/ownership suite. BindingTest is standalone MVC with mocks. PostgreSQL suites additionally exercise persisted authorization, version/overlap/quotas and unchanged business guards. These results are actual runtime evidence, with the provenance limitations above.

## QĐ review checklist and target drift

QĐ independent completion/sign-off is PENDING in the latest observed discussion (earlier comment says independent full run started). HP preliminary static review is not APPROVED. Review the upstream12-commit QA-01 delta, form/disable allowlists, CSRF filter ordering and encoded-path matcher, persisted role/ownership/version checks, promotion/voucher interactions, new raw-artifact metadata and source equivalence. Confirm the scope explicitly before final acceptance.

Current develop has advanced from b538dbc to `fb7dcebd434c6887396881e9b34df1bf12d8250e`: PR is ahead7/behind2. The two commits only change README, task board and QA-01 documentation; no application/test/build inputs. Code HEAD is held during QĐ verification. No behind0 or validation against a newly merged target is claimed; HP must decide whether a later documentation sync is required.

## History and non-overlapping count reconciliation

Historical8bab8dd:686+1325=2011 PASS. Historicalc593352:697+1326=2023 PASS. Same-source8705 sandbox attempt:705 unit PASS,73 PostgreSQL startup errors, Maven exit1. New external run:705+1352=2057 PASS artifacts. Runs are never combined or relabelled. The sandbox raw Maven/Node/tooling logs were rehashed against their immutable manifest; working target XML was replaced by the user's clean run, so no fresh rehash of old failed XML is claimed.

| Group | Suite | c593352 | Outside run | Delta |
|---|---|---:|---:|---:|
| unit | `com.uteexpress.identity.service.OtpSecurityTest` | 3 | 4 | +1 |
| unit | `com.uteexpress.security.OtpRequestLimitFilterTest` | 0 | 2 | +2 |
| unit | `com.uteexpress.security.OtpRequestRateLimiterTest` | 0 | 5 | +5 |
| postgres | `com.uteexpress.security.Qa01CookieCsrfIT` | 0 | 15 | +15 |
| postgres | `com.uteexpress.security.Qa01OtpSecurityIT` | 0 | 11 | +11 |

Node33 and tooling17 are reused from the previously verified exact same8705 source and rehashed original logs; neither was rerun in this external Maven execution. Their original environment failures/accommodations remain explicit. No old2011/2023 result is relabelled post-sync.

READY FOR QĐ EVIDENCE REVIEW; **HP FINAL ACCEPTANCE PENDING** because QĐ verdict, provenance evaluation and current-develop documentation sync decision remain outstanding. No PR approval/merge, finding closure, QA-02 completion or change to Q70-S01 ACCEPTED RISK — LIMITED.
