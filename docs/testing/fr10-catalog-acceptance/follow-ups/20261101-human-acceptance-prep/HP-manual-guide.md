# HP — kiểm tra trực tiếp 36 mục Catalog

**Phiên local riêng, Guest, đúng source 4d57f30. Không thao tác Render.**

Mở [http://127.0.0.1:62551](http://127.0.0.1:62551). JAR `0a57193273f330b028c134b196977b626c643b7582de3e7a8aefcc23046acc36`. Phiên bắt đầu `2026-11-01T06:55:24.172606+00:00`, có lease kỹ thuật 90 phút; nếu hết hạn, yêu cầu chuẩn bị phiên mới, không dùng lại port cũ. Chuẩn bị phiên không có nghĩa là nghiệm thu.

## Cách chọn viewport

Trong Chrome/Brave: F12 → Ctrl+Shift+M → chọn Responsive → nhập Width/Height. Kiểm tra lần lượt **375×812**, **768×1024**, **1440×900**; giữ page zoom 100%. Click vào trang trước khi dùng Tab/Shift+Tab. Đây là viewport mô phỏng, không phải bằng chứng thiết bị vật lý.

Làm H01–H12 tại từng viewport. Ghi PASS/FAIL/BLOCKED/NOT RUN riêng. Chỉ xác nhận PASS sau khi tự thao tác; ảnh reference và automation không thay quyết định của bạn. Không nhập mật khẩu hoặc đăng ký tài khoản trong phiên Guest này.

| Nhóm | Trang / thao tác trực tiếp | Cần quan sát |
| --- | --- | --- |
| H01 | [/](http://127.0.0.1:62551/) — Đọc hero, danh mục/cửa hàng và12 card; scroll đến footer. | Nội dung và link rõ; có data thì không hiện empty state. |
| H02 | [/categories/demo-category](http://127.0.0.1:62551/categories/demo-category) — Mở từ link danh mục ở Home; xem12 card. | Tên đúng, chỉ eligible products; không thông báo trống. |
| H03 | [/products/1](http://127.0.0.1:62551/products/1) — Mở Bưu kiện mẫu tiêu chuẩn từ card; xem ảnh, giá, stock, mô tả và login CTA. | Giá125.000đ, stock12, ảnh fixture đọc được; Guest không có form add cart. |
| H04 | [/shops](http://127.0.0.1:62551/shops) — Mở Cửa hàng từ navbar; xem các shop và link. | Chỉ approved shops, không thông báo không có shop khi có3 card. |
| H05 | [/shops/demo-shop](http://127.0.0.1:62551/shops/demo-shop) — Mở FR10 Demo shop từ directory/product; đọc7 card gồm escape fixture. | Không lẫn sản phẩm shop2; không hiện empty state khi có data. |
| H06 | [/categories/fr10-empty](http://127.0.0.1:62551/categories/fr10-empty) — Mở active category không có sản phẩm. | Empty state rõ, không card giả; có đường quay lại. |
| H07 | [/shops/fr10-empty-shop](http://127.0.0.1:62551/shops/fr10-empty-shop) — Mở approved shop không có sản phẩm. | Empty state đúng, không lỗi/tràn; có đường về catalog. |
| H08 | [/products/2](http://127.0.0.1:62551/products/2) — Tra zeroStock trong dataset; mở chi tiết. | Vẫn hiển thị công khai và ghi Hết hàng. |
| H09 | [/](http://127.0.0.1:62551/) — Home→product→category→product→shop bằng link; thử Back. Bấm Back/Alt+Left sau khi đến shop, xác nhận quay lại đúng trang trước. | Đến đúng trang, không cần tự đoán route. |
| H10 | [/](http://127.0.0.1:62551/) — Tab từ đầu trang; mở menu375/768 và chờ animation xong rồi đóng; desktop xem navbar. Tab/Shift+Tab qua các link và nút; dùng Enter/Space trên nút menu; mở/đóng ổn định và thử chạm nhanh. Không bị kẹt focus; không mất link. Desktop không cần nút collapse. | Focus rõ, không trap, menu operable và không mất link. Ghi riêng cảm nhận fast-tap. |
| H11 | [/products/17](http://127.0.0.1:62551/products/17) — Tra xss ID trong dataset; đọc tên script fixture. | Tên hiển thị như văn bản, không popup/chạy script. |
| H12 | [/products/9223372036854775807](http://127.0.0.1:62551/products/9223372036854775807) — Mở ID không tồn tại; xem error, dùng Back về Catalog. Hiện tại lỗi là JSON an toàn. HP phải đánh giá JSON này có chấp nhận được cho scope Catalog không; thử Back về trang Catalog đã mở trước đó. Không tự mặc định JSON usability PASS. | Không SQL/stacktrace; ghi cảm nhận JSON error usability, không tự sửa contract. |

## Trả lời kết quả

Có thể trả lời theo viewport, ví dụ `375: H01–H08 PASS; H09 FAIL (Back về sai trang); H10 NOT RUN; H11 PASS; H12 BLOCKED (không đọc được)`. Nếu thực sự đã tự kiểm tra hết và đều đạt, ghi `375: H01–H12 PASS`. Làm tương tự cho 768 và 1440.

Reviewer phải tự xác nhận danh tính/role HP và browser/device đã dùng. Chat confirmation sẽ được ghi nhận đúng nguồn; không tạo chữ ký tay hoặc bịa thời điểm quan sát từng item. Agent chỉ ghi thời điểm tiếp nhận phản hồi thực tế khi có.

Nếu có lỗi, giữ mã mục và mô tả, không tự sửa production trong task này. Sau khi hoàn tất, báo `xong phiên Catalog` để operator chụp fingerprint cuối và cleanup đúng tài nguyên riêng. Các ảnh/bằng chứng mới nếu có phải vào một attempt mới; không ghi đè các file cũ.

Danh sách 36 dòng và reference screenshots: [human-review-matrix.md](human-review-matrix.md).
