# PR75 authorized OTP-copy correction and FR-01/FR-02 handoff

Master Plan re-read SHA25618523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a. Priority75/G01 before final69/G02. HP authorization was explicitly relayed in the current human task; no fabricated GitHub approval URL.

## Exact scope

Fresh executed source **53fbf923009c1d9da2ae0c73440b6a6de14904e5**, tree8ba39ba27ed69771a96efd54c2c6e4d189e3d404. Accepted develop6a26370 already contains70/74;75 was clean/behind0, no redundant merge. Previous75head3c4c001 changed by exactly **one HTML help-text line** at `src/main/resources/templates/auth/verify-otp.html:42`: “Mã có hiệu lực trong 10 phút.” → **“Mã có hiệu lực tối đa 5 phút.”** application.yml77/PT5M, OtpProperties16-17/max5, all OTP backend/security/business/refund/migration files are unchanged. Other local/remote branch contents were not modified.

## Fresh focused verification

**28unit +57PostgreSQL/5classes PASS**,48Node and17tooling PASS,0failures/errors/skips/flaky records,exit0/no timeout. Focused unit selection covers Architecture/EmailVerificationController/EmailVerificationService/OTP security/request limiter/filter. PostgreSQL selection: Auth03EmailVerificationIT, Qa01OtpSecurityIT, Qa01CookieCsrfIT, ShipmentAssignmentIT (including two real Order-lock races), ShipperFulfillmentIT.11XML/log hashes rechecked. This is focused regression, not full709unit/313PG/2124G02 evidence. Prior source packets remain historical and are not relabeled current.

Actual packaged JAR was hashed and frozen locally. Its packaged OTP template contains corrected copy and no10minute copy; source/tree/JAR/template/migrations and wrapper identities are in the handoff. Clean-worktree guard passed before/after build and at handoff snapshot; no bypass.

**18fresh automated Chrome render/assignment cases PASS** at375/768/1440: Admin assignment/reassignment/stale/lockedShipper/pickup, Manager flow, missingCSRF/duplicate/nonOps denial, competing Admin+Manager requests, keyboard/focus and corrected OTP help text/ARIA association. Screenshots/actor/routes/source/JAR/dataset/UTC/hash metadata and owned environment cleanup are published. Three representative originals visually inspected by agent; no human/every-pixel audit claimed.

CI still explicitly runs all8assignment Node tests among48. Exact publication-head CI and workflow log must be checked separately, not inherited from prior source. PR70-only PostgreSQL job is conditionally not applicable on75; local57PG verification is separate.

## FR-01 / FR-02

Verified QD environment: Java21, Maven wrapper3.9.11, Node22.21.0, Python3.13.9, PostgreSQL17.6 observed in runner log, Docker29.4.1 accessible in the actual Maven session; actual OS/image/wrapper hashes/commands/times/exits are recorded.28SQL migration versions are unique and unchanged from accepted develop.

Raw regression XML/logs plus separately labeled baseline8a96 XML and the exact candidate JAR are packaged in a private local ZIP with hash. ZIP integrity checked. **Not delivered to TD, not publicly committed.** Public receipt/metadata do not imply TD independently reviewed or reran them. TD/HP choose an approved private transfer channel and TD confirms receipt/hash. Independent TD environment/Docker access and FR disposition remain pending.

## Acceptance boundary

OTP-COPY is technically fixed in candidate under HP authorization, owner/final acceptance pending. Genuine human G01 checklist is updated for this source; unexecuted cells remain NOT RUN/unsigned. Physical-device/IME/screen-reader outcomes are not fabricated. TD reviews exact newHEAD/FR-01/FR-02/compatibility; HP decides G01 acceptance/merge. No APPROVE/MERGE/forcepush, no QA03 COMPLETE, no new finalG02 on unmerged75. Raw credentials/JWT/CSRF/session config/dumps/private logs are excluded from public commits.
