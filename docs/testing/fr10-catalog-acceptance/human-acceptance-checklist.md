# HP manual Catalog acceptance — UNSIGNED

**Không có human PASS được điền trước.** Cần fresh owned environment đúng source/fixture, do operator cung cấp base URL trong phiên. Toàn bộ actual/result/reviewer/time/signature bên dưới để trống. Status tổng thể PENDING / NOT RUN; chỉ HP ghi sau khi tự thao tác.

Nguồn `a5620f1`, Guest; viewport375×812/768×1024/1440×900. Container của automated runs đã cleanup; ảnh là reference, không là session đang mở. ID product1 chỉ đúng fixture này; khi chuẩn bị session mới phải đối chiếu dataset, không sửa DB để khớp ID. Ghi source/JAR/dataset hash, browser/version, OS/device và base URL trước khi bắt đầu.

Base URL: ______  Source/JAR: ______  Dataset: ______  Device/OS/browser/version: ______

## H01 — Trang chủ
Route `/`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Đọc hero, danh mục/cửa hàng và12 card; scroll đến footer.
Expected: Nội dung và link rõ; có data thì không hiện empty state.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H01-375|375px|[reference](attempts/baseline-1/screenshots/CAT-HOME-375.png)| | | | | | |
|H01-768|768px|[reference](attempts/baseline-1/screenshots/CAT-HOME-768.png)| | | | | | |
|H01-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-HOME-1440.png)| | | | | | |

## H02 — Category
Route `/categories/demo-category`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Mở từ link danh mục ở Home; xem12 card.
Expected: Tên đúng, chỉ eligible products; không thông báo trống.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H02-375|375px|[reference](attempts/baseline-1/screenshots/CAT-CATEGORY-375.png)| | | | | | |
|H02-768|768px|[reference](attempts/baseline-1/screenshots/CAT-CATEGORY-768.png)| | | | | | |
|H02-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-CATEGORY-1440.png)| | | | | | |

## H03 — Product detail
Route `/products/1`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Mở Bưu kiện mẫu tiêu chuẩn từ card; xem ảnh, giá, stock, mô tả và login CTA.
Expected: Giá125.000đ, stock12, ảnh fixture đọc được; Guest không có form add cart.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H03-375|375px|[reference](attempts/baseline-1/screenshots/CAT-PRODUCT-375.png)| | | | | | |
|H03-768|768px|[reference](attempts/baseline-1/screenshots/CAT-PRODUCT-768.png)| | | | | | |
|H03-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-PRODUCT-1440.png)| | | | | | |

## H04 — Shop directory
Route `/shops`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Mở Cửa hàng từ navbar; xem các shop và link.
Expected: Chỉ approved shops, không thông báo không có shop khi có3 card.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H04-375|375px|[reference](attempts/baseline-1/screenshots/CAT-SHOPS-375.png)| | | | | | |
|H04-768|768px|[reference](attempts/baseline-1/screenshots/CAT-SHOPS-768.png)| | | | | | |
|H04-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-SHOPS-1440.png)| | | | | | |

## H05 — Shop detail
Route `/shops/demo-shop`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Mở FR10 Demo shop từ directory/product; đọc7 card gồm escape fixture.
Expected: Không lẫn sản phẩm shop2; không hiện empty state khi có data.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H05-375|375px|[reference](attempts/baseline-1/screenshots/CAT-SHOP-375.png)| | | | | | |
|H05-768|768px|[reference](attempts/baseline-1/screenshots/CAT-SHOP-768.png)| | | | | | |
|H05-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-SHOP-1440.png)| | | | | | |

## H06 — Empty category
Route `/categories/fr10-empty`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Mở active category không có sản phẩm.
Expected: Empty state rõ, không card giả; có đường quay lại.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H06-375|375px|[reference](attempts/baseline-1/screenshots/CAT-EMPTY-CATEGORY-375.png)| | | | | | |
|H06-768|768px|[reference](attempts/baseline-1/screenshots/CAT-EMPTY-CATEGORY-768.png)| | | | | | |
|H06-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-EMPTY-CATEGORY-1440.png)| | | | | | |

## H07 — Empty shop
Route `/shops/fr10-empty-shop`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Mở approved shop không có sản phẩm.
Expected: Empty state đúng, không lỗi/tràn; có đường về catalog.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H07-375|375px|[reference](attempts/baseline-1/screenshots/CAT-EMPTY-SHOP-375.png)| | | | | | |
|H07-768|768px|[reference](attempts/baseline-1/screenshots/CAT-EMPTY-SHOP-768.png)| | | | | | |
|H07-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-EMPTY-SHOP-1440.png)| | | | | | |

## H08 — Zero-stock detail
Route `/products/<zeroStock>`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Tra zeroStock trong dataset; mở chi tiết.
Expected: Vẫn hiển thị công khai và ghi Hết hàng.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H08-375|375px|[reference](attempts/baseline-1/screenshots/CAT-ZERO-STOCK-375.png)| | | | | | |
|H08-768|768px|[reference](attempts/baseline-1/screenshots/CAT-ZERO-STOCK-768.png)| | | | | | |
|H08-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-ZERO-STOCK-1440.png)| | | | | | |

## H09 — Browsing navigation
Route `/`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Home→product→category→product→shop bằng link; thử Back.
Expected: Đến đúng trang, không cần tự đoán route.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H09-375|375px|[reference](attempts/baseline-1/screenshots/CAT-NAVIGATION-375.png)| | | | | | |
|H09-768|768px|[reference](attempts/baseline-1/screenshots/CAT-NAVIGATION-768.png)| | | | | | |
|H09-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-NAVIGATION-1440.png)| | | | | | |

## H10 — Keyboard/menu
Route `/`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Tab từ đầu trang; mở menu375/768 và chờ animation xong rồi đóng; desktop xem navbar.
Expected: Focus rõ, không trap, menu operable và không mất link. Ghi riêng cảm nhận fast-tap.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H10-375|375px|[reference](attempts/menu-recheck-2/screenshots/CAT-KEYBOARD-MENU-375.png)| | | | | | |
|H10-768|768px|[reference](attempts/menu-recheck-2/screenshots/CAT-KEYBOARD-MENU-768.png)| | | | | | |
|H10-1440|1440px|[reference](attempts/menu-recheck-2/screenshots/CAT-KEYBOARD-MENU-1440.png)| | | | | | |

## H11 — Escaped text
Route `/products/<xss>`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Tra xss ID trong dataset; đọc tên script fixture.
Expected: Tên hiển thị như văn bản, không popup/chạy script.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H11-375|375px|[reference](attempts/baseline-1/screenshots/CAT-ESCAPING-375.png)| | | | | | |
|H11-768|768px|[reference](attempts/baseline-1/screenshots/CAT-ESCAPING-768.png)| | | | | | |
|H11-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-ESCAPING-1440.png)| | | | | | |

## H12 — Error readability
Route `/products/9223372036854775807`; actor Guest. Preconditions: fresh Catalog fixture theo README, không dùng account/DB thật.
Steps: Mở ID không tồn tại; xem error, dùng Back về Catalog.
Expected: Không SQL/stacktrace; ghi cảm nhận JSON error usability, không tự sửa contract.

| Test ID | Viewport | Screenshot reference (automated only) | Actual observed | Human PASS/FAIL/BLOCKED/NOT RUN | Finding | Reviewer | UTC timestamp | Signature |
|---|---|---|---|---|---|---|---|
|H12-375|375px|[reference](attempts/baseline-1/screenshots/CAT-SAFE-ERROR-375.png)| | | | | | |
|H12-768|768px|[reference](attempts/baseline-1/screenshots/CAT-SAFE-ERROR-768.png)| | | | | | |
|H12-1440|1440px|[reference](attempts/baseline-1/screenshots/CAT-SAFE-ERROR-1440.png)| | | | | | |

## Chốt quyết định

36 dòng cần HP quan sát thực tế. Kết quả automated hiện có finding FR10-CAT-01; không tự ký accepted chỉ vì CI xanh. Owner fix hoặc disposition cần quyết định riêng, rồi kiểm tra lại cả empty và populated states.

HP final decision: ______  Timestamp: ______  Signature: ______

Real-device/screenreader/IME/zoom khác viewport và performance không được automation này chứng minh; ghi NOT RUN/limitation hoặc yêu cầu scope decision riêng, không tự waiver.
