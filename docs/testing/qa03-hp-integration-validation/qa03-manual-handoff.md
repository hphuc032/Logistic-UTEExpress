# QA-03 G01 — Bàn giao thực thi browser/manual

**Chưa chạy:0/24; tất cả NOT RUN.** Công cụ QĐ thử lại ngày2026-10-29 vẫn lỗi khởi tạo kernel assets/os error3 trước UI. Đây là lỗi công cụ, không phải bằng chứng application FAIL/PASS. Cần khôi phục supported runtime hoặc chỉ định người thực hiện HP/UI/QĐ thủ công.

Trước khi bắt đầu: HP/nhóm thống nhất source SHA và dataset/manifest. Dùng môi trường disposable/local với actors giả BuyerA/B, VendorA/B, Admin/Manager và ShipperA/B; không production data. Không dùng SQL tạo status/receipt/payment để giả hành trình. Credentials chuyển riêng, không ghi vào evidence. Hiện seed runner tự dọn tài nguyên cuối run; không mặc định rằng DB/app của rehearsal trước vẫn còn chạy. Owner phải chuẩn bị app/fixture được duyệt còn hoạt động cho phiên manual; không phá cleanup guards để có screenshot.

Ghi source SHA, build SHA, dataset SHA/manifest, executor, browser/version, viewport(width/height), ngày giờ, bước thực hiện, expected/actual, PASS/FAIL/NOT RUN và finding cho từng dòng. Screenshot không chứa cookie/JWT/CSRF/password/address/phone thực. Nếu thay source/dataset, tách run mới; không sửa trạng thái cũ hoặc suy ra PASS từ screenshot của scope khác.

## Bước theo nhóm

- **assignment-reassignment**: Dùng Admin/Manager assign/reassign trước pickup; kiểm tra stale version, Shipper locked, sau pickup. Chụp form, kết quả và lỗi không lộ dữ liệu riêng tư.
- **failure-retry**: Shipper được assign thực hiện failure1 -> retry2 -> failure2; thử retry3. Kiểm tra attempt, evidence và lỗi hiển thị.
- **attempt2-delivery**: Dùng Shipment khác, failure1 -> retry2 -> delivered với đúng COD; thử duplicate. Không tự SQL sửa Order/Payment.
- **return-to-sender**: Đơn exhausted riêng; ghi trusted receipt và Ops xác nhận bằng workflow đã duyệt; thử replay. Không dùng Buyer Return thay RTS.
- **notification-two-sessions**: Dùng các browser profile riêng BuyerA/BuyerB/Vendor. Vendor CONFIRM; kiểm tra đúng inbox/badge, offline/reconnect/read/logout và không rò sang BuyerB.
- **review-media**: Từ owned DELIVERED Order mở server-derived item link; thử rating/text/ảnh hợp lệ và invalid/duplicate/foreign. Kiểm tra public/hidden media và policy sau Return bằng workflow.
- **admin-financial-scope**: Admin xem dashboard/filter; Manager truy cập URL tài chính phải bị deny. Kiểm tra empty/unavailable/negative labels không tràn màn hình.
- **full-user-journey**: Register -> catalog/cart/checkout -> Vendor confirm/ready -> assign/pickup/deliver -> Buyer Review; kiểm tra controls, lỗi và navigation trên từng width.

## Matrix cần nộp

Mỗi dòng bắt buộc actual result và screenshot/record phù hợp. File gợi ý dưới đây **chưa tồn tại**, không phải bằng chứng đã chụp. Điền tên executor ở run header trước khi thực hiện.

| Case | Width px | Role | Expected | Current | Suggested evidence filename |
|---|---:|---|---|---|---|
| assignment-reassignment | 375 | ADMIN/MANAGER | Assignment succeeds before pickup; stale/foreign/ineligible paths preserve state | NOT RUN | `assignment-reassignment-375.png` |
| assignment-reassignment | 768 | ADMIN/MANAGER | Assignment succeeds before pickup; stale/foreign/ineligible paths preserve state | NOT RUN | `assignment-reassignment-768.png` |
| assignment-reassignment | 1440 | ADMIN/MANAGER | Assignment succeeds before pickup; stale/foreign/ineligible paths preserve state | NOT RUN | `assignment-reassignment-1440.png` |
| failure-retry | 375 | SHIPPER | Failure 1, retry 2 and failure 2; no retry 3, readable evidence/errors | NOT RUN | `failure-retry-375.png` |
| failure-retry | 768 | SHIPPER | Failure 1, retry 2 and failure 2; no retry 3, readable evidence/errors | NOT RUN | `failure-retry-768.png` |
| failure-retry | 1440 | SHIPPER | Failure 1, retry 2 and failure 2; no retry 3, readable evidence/errors | NOT RUN | `failure-retry-1440.png` |
| attempt2-delivery | 375 | SHIPPER | Delivery attempt 2 with exact COD succeeds once | NOT RUN | `attempt2-delivery-375.png` |
| attempt2-delivery | 768 | SHIPPER | Delivery attempt 2 with exact COD succeeds once | NOT RUN | `attempt2-delivery-768.png` |
| attempt2-delivery | 1440 | SHIPPER | Delivery attempt 2 with exact COD succeeds once | NOT RUN | `attempt2-delivery-1440.png` |
| return-to-sender | 375 | ADMIN | Trusted receipt and replay, no buyer-return/refund crossover | NOT RUN | `return-to-sender-375.png` |
| return-to-sender | 768 | ADMIN | Trusted receipt and replay, no buyer-return/refund crossover | NOT RUN | `return-to-sender-768.png` |
| return-to-sender | 1440 | ADMIN | Trusted receipt and replay, no buyer-return/refund crossover | NOT RUN | `return-to-sender-1440.png` |
| notification-two-sessions | 375 | BUYER A/B + VENDOR | Vendor confirm reaches only persisted Buyer; offline/reconnect/read/logout work | NOT RUN | `notification-two-sessions-375.png` |
| notification-two-sessions | 768 | BUYER A/B + VENDOR | Vendor confirm reaches only persisted Buyer; offline/reconnect/read/logout work | NOT RUN | `notification-two-sessions-768.png` |
| notification-two-sessions | 1440 | BUYER A/B + VENDOR | Vendor confirm reaches only persisted Buyer; offline/reconnect/read/logout work | NOT RUN | `notification-two-sessions-1440.png` |
| review-media | 375 | BUYER + public | Owned delivered item, validation/duplicate/foreign errors, public media after Return | NOT RUN | `review-media-375.png` |
| review-media | 768 | BUYER + public | Owned delivered item, validation/duplicate/foreign errors, public media after Return | NOT RUN | `review-media-768.png` |
| review-media | 1440 | BUYER + public | Owned delivered item, validation/duplicate/foreign errors, public media after Return | NOT RUN | `review-media-1440.png` |
| admin-financial-scope | 375 | ADMIN/MANAGER | Admin metrics usable; Manager cannot access private finance | NOT RUN | `admin-financial-scope-375.png` |
| admin-financial-scope | 768 | ADMIN/MANAGER | Admin metrics usable; Manager cannot access private finance | NOT RUN | `admin-financial-scope-768.png` |
| admin-financial-scope | 1440 | ADMIN/MANAGER | Admin metrics usable; Manager cannot access private finance | NOT RUN | `admin-financial-scope-1440.png` |
| full-user-journey | 375 | BUYER/VENDOR/SHIPPER/ADMIN | Register, catalog, cart, checkout, delivery and review controls usable | NOT RUN | `full-user-journey-375.png` |
| full-user-journey | 768 | BUYER/VENDOR/SHIPPER/ADMIN | Register, catalog, cart, checkout, delivery and review controls usable | NOT RUN | `full-user-journey-768.png` |
| full-user-journey | 1440 | BUYER/VENDOR/SHIPPER/ADMIN | Register, catalog, cart, checkout, delivery and review controls usable | NOT RUN | `full-user-journey-1440.png` |

## Điều kiện bàn giao

QD đối chiếu đủ24 dòng, các đường role/owner/foreign và errors, source/dataset/capture identity, screenshots thực, finding severity/owner và log đã lọc nếu cần. TD review luồng Order/Payment/Return/Refund, HP/UI review security/business/responsive và quyết định acceptance. Không tự đánh dấu G01 PASS chỉ vì hoàn thành đủ file; finding phải có disposition và evidence tương ứng. G01/G02 là gate riêng, seed/automation PASS không thay manual.
