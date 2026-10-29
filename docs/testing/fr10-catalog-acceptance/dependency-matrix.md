# Dependency và giới hạn scope

| Dependency / namespace | Evidence hiện có | Có thể làm hiện tại | Acceptance còn thiếu |
|---|---|---|---|
| Master Plan FR-10 Catalog | Master Plan hash trong source-proof; HP chat clarification | Home/category/product/shop, public visibility/media và responsive | HP36 dòng manual; finding UI còn OPEN |
| Master Plan FR-09 Product | Source ProductDemoSeeder/ProductRepository/migrations hiện có trong accepted develop | Fixture Catalog:12 active demo products, hidden/moderated, test cases thêm; không nhận nghiệm thu CRUD Vendor | Không claim toàn FR-09 sign-off |
| Master Plan FR-06 Profile / FR-07 Address | Functional requirements khác, không là dependency trực tiếp của Public FR-10 | Không cần dùng profile/address cho Guest browsing | Ngoài scope, không đổi status |
| QA-03 shorthand FR-06 combined seed | PR69 preflight c5ce35c; latest additive3ba76d3 records FR06 ACCEPTED WITH DOCUMENTED LIMITATIONS; direct decision/raw-review links pending | Đọc metadata/historical limits; không chạy hay dùng seed này; không gọi đây là independent verification của FR10 | Không tự coi executed=accepted |
| QA-03 shorthand FR-07 independent gate | Latest3ba76d3 ghi AUTHORIZED / NOT RUN, TD owner/QĐ support | Chỉ ghi dependency mở của QA-03, không chạy | TD/HP/QĐ theo authorization riêng |
| G01 / T43–T47 assignment/shipping/dashboard/socket/notification | PR75 merged; human G01 pending | Không execute trong Catalog scope đã chốt | Ngoài scope; không đóng các gate này |
| T48 database | Ba disposable PG17.6 boots/migrate28/schema validate/fingerprint | Evidence môi trường cục bộ cho Catalog | Không full seed/migration acceptance hoặc backup restore |
| T49 deployment | Explicit Java21/UTC, localhost app, private uploads | Ghi actual runtime | Không SMTP/TLS/proxy/ACL/restart/load/release acceptance |
| T50 | Catalog pages375×812/768×1024/1440×900 | Responsive/keyboard/menu tool-driven | Human, real device, IME/screenreader và Register→Delivery→Review chưa được task này nghiệm thu |
| QA-02 FR readiness checklist | NOT LOCATED trong các refs được inspect | Dùng Master Plan + xác nhận HP; không suy đoán mã gate mới | TD bổ sung canonical path nếu cần cho task release riêng |

Không có deadline, waiver hoặc risk acceptance mới trong packet. QA02/QA03/REL statuses giữ nguyên.


## Final dependency read-back

During publication prep, PR69 advanced from c5ce35c to docs-only3ba76d3. Latest additive record states FR06 ACCEPTED WITH DOCUMENTED LIMITATIONS, TD raw verification PASS per supplied decision (not TD seed execution), and FR07 AUTHORIZED / NOT RUN, TD owner/QĐ support only. Direct HP decision and TD raw-review URL/hash remain pending traceability. This read-back preserves the original execution-time PENDING labels and all original attempts. It neither reruns those gates nor expands their acceptance. See [latest dependency observation](latest-dependency-observation.json). Develop and Catalog tested source remain a5620f1.
