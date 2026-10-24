# REV-01 — integrated Order boundary and Review writer

Supersedes the foundation-only handoff for current behavior. Integrated TD Order eligibility f70d942 and buyer item ID 9a2abf6 on the existing QD branch. The two branches remain preserved in merge history; no rewrite/force-push or direct develop merge.

## Write and attribution

POST /order-items/{id}/review supports JSON rating/comment and multipart rating/comment/images. GET on the same path displays the Review form only after TD's current-user eligibility read. USER/VENDOR method guards, existing CSRF and strict request/query/part allowlists apply. No browser buyer, Product, Order, visibility or storage key authority. JSON rejects unknown fields under the existing global Jackson policy.

ReviewService has no outer transaction. TD OrderReviewWriteBoundary rejects outer transactions, starts READ_COMMITTED, locks/rechecks Order -> Account and invokes the QD MANDATORY writer. Review uses only ReviewableOrderItem server facts. Uniqueness/composite FKs remain authoritative. Duplicate writes map to CONFLICT after whole-transaction rollback. No Order status/version/history/event, Payment, inventory, voucher or Return mutation is performed by Review.

Images are stored inside that joined callback using the existing signature/metadata/dimension-validated FileStorageService, max 3, max 5 MiB each, dimensions <=4096. Image-only JPEG/PNG/WebP; no video/SVG/HTML/client keys. The writer registers rollback compensation before file writes; media failure, callback failure or DB rollback deletes already-stored keys. Filesystem/DB are not a distributed atomic transaction: cleanup I/O failure is logged for reconciliation, and process crash between file creation and DB outcome remains an orphan-file reconciliation limitation. No claim that DB rollback can roll back the filesystem by itself.

Multipart request envelope increased 7MB ->16MB so three 5MiB images plus fields can be submitted; existing 6MB parser per-file limit remains, controller/storage enforce the stricter 5MiB Review limit. This shared configuration change needs HP security review. Payload bytes are loaded only after count/size checks. Empty browser file inputs are omitted.

## UI/public display

TD's persisted orderItemId extends the existing buyer detail, preserving Vendor DTO compatibility. The buyer Order detail links to Review form for DELIVERED items; product detail links to public reviews. The server always rechecks eligibility on POST. These are minimal template integrations requiring TD/HP owner review.

GET /products/{id}/reviews lists at most 20 visible Reviews per page, bounded page 0..10000; no buyer address/phone/username or storage key exposed. Comments render through th:text, never HTML. Public image URL uses numeric Product/media IDs and requires PUBLIC Review AND current public Product/Shop/category through Catalog authority. Hidden reviews/products return no media. Image content is nosniff, cache no-store; reviews use persisted ordering/time. Catalog visibility and Review read share a read-only REPEATABLE_READ snapshot.

Existing Reviews remain visible after a later Return/Refund unless Review visibility or Product publication changes; creation is restricted to DELIVERED at the write serialization point. This is an engagement-policy choice for HP/TD review, not a grant to create on RETURNED/REFUNDED. Review-first followed by Return may both commit; Return-first prevents new Review.

## Verification and review gates

Focused integrated run: ArchitectureTest + ReviewFoundationTest (14), ReviewIntegrationIT (14), OrderEligibilityIT (26) and BuyerOrderIT (35) PASS, exit0. Earlier attempts found only test scaffolding issues: a helper name shadowed Spring Security user(), oversized fixture username, and Mockito stubbing through a MANDATORY proxy outside a transaction. Tests were corrected to use schema-valid fixtures and unwrapped target spies; production transaction guards were not relaxed. Incomplete/failed attempts are not counted as PASS.

Final source adds actual buyer-detail link/JSON success and four forbidden upload cases (too many, oversize, SVG, metadata mismatch). Full Java21 -Ppostgres-it clean verify is running on that source; final result/head and exact-head CI will be recorded in PR. Do not substitute earlier 604-unit/12-migration foundation results for full vertical-slice acceptance.

ReviewIntegrationIT exercises true server facts, same-transaction Review/media, escaped public HTML and media, hidden visibility, role/CSRF/forged field denial, foreign/locked/revoked identity, current Order state, VENDOR as buyer, outer-transaction rejection, concurrent duplicate writers, file compensation and real Review-vs-Return operations in BOTH schedules. Real PostgreSQL waits are observed via pg_blocking_pids. The test holds writer/Return-authority stages with spies but never replaces actual locks, callbacks or writes.

TD must review final Order integration, item mapping and race semantics; HP reviews validation/media limits, publication policy, authorization and acceptance. Draft remains until full verification gates complete. No merge or GitHub approval performed. No WS-02, video/REV-02, editing/moderation UI or new financial scope.

## HP final-policy follow-up

HP approved the existing authorization/publication/upload envelope and continued PUBLIC visibility after Return/Refund. Follow-up fixes leave TD eligibility, transaction/order locking, schema and amount policy unchanged. ReviewContent now counts Unicode code points (matching PostgreSQL char_length), rejects unpaired UTF-16 surrogates and keeps trim/NUL/rating checks. Browser UTF-16 minlength/maxlength attributes are removed; server enforces the exact Unicode length and form text explains it.

Rollback compensation now deletes only on definite ROLLED_BACK. UNKNOWN never deletes potentially committed media and emits review_media_cleanup_deferred. Deletion failure emits review_media_cleanup_failed with correlation/key/status/failure class for restricted operational logging. The reconciliation runbook requires all writers paused/drained, primary DB reference checks including HIDDEN, no symlink/namespace escape and dry-run/reversible quarantine; age alone never authorizes deletion. Production durable log collection/alerts and operator procedure must be verified before deployment.

Focused HP-fix run passed ReviewIntegrationIT 24 cases plus Architecture/ReviewFoundation tests before the additional UNKNOWN-status branch regression. Final full clean PostgreSQL verification on the final source (including UNKNOWN defer) is running; PR records the final SHA/results. No prior 1,721-test result is substituted for this changed source. See REV-01-media-reconciliation-runbook.md for safety/limitations.
