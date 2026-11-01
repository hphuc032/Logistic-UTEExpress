# Kịch bản video và storyboard phần HP

Master Plan phầnP quy định demo nhóm khoảng14 phút; phần HP0:00–3:45, không tự chiếm thời lượng TD/QĐ. Không quay mặt hay ghi video tự động trong DOC-01. Lời thoại dưới đây là kịch bản mới soạn, không phải biên bản demo đã chạy.

| Mốc | Màn hình và thao tác | Lời thoại gợi ý | Bằng chứng cần ghi |
|---|---|---|---|
|0:00–0:45|Trang chủ, sơ đồ architecture và GitHub|Em là Hoàng Phúc, phụ trách architecture, auth/security, account/address, shop/catalog và shared UI. UTEExpress là modular monolith ba tầng. TD phụ trách commerce; QĐ phụ trách governance và giao nhận.|Source SHA, sơ đồ, phân công; không hiện ENV|
|0:45–1:30|Home → /products search/filter → detail → shop|Đây là catalog từ DB, chỉ Product ACTIVE, Shop APPROVED, Category active. Giá được đọc phía server. Em đổi lọc/sort và trang, sau đó xem ảnh/chi tiết shop.|Dữ liệu≥10 sản phẩm nhiều shop; bố cục375/768/desktop đã chuẩn bị|
|1:30–3:00|/register → /verify-otp → /login|Em đăng ký một tài khoản demo mới. Server chuẩn hóa identity, BCrypt password và gán USER/PENDING. OTP đúng purpose mới kích hoạt. Đăng nhập cấp JWT HttpOnly; request sau còn kiểm tra ACTIVE, role và tokenVersion trong DB.|SMTP test hợp lệ; không quay rawOTP/password/JWT; nhập code ngoài khung ghi khi cần|
|3:00–3:45|/user/profile → /user/addresses → Vendor product form bằng tài khoản riêng|Các form không nhận ownerId. Địa chỉ được truy vấn theo principal và chỉ có một default. Tài khoản Vendor chỉ sửa sản phẩm thuộc shop đã duyệt. Em bàn giao Buyer đã sẵn sàng cho TD thao tác giỏ và checkout.|Foreign-ID negative đã chuẩn bị an toàn; không thay quyền trên UI để demo|
|Phần bổ sung ngoài14phút nếu giảng viên yêu cầu|Logout, forgot/reset, source method, setup|Logout/reset tăng tokenVersion để JWT cũ bị từ chối. POST vẫn có CSRF vì token nằm trong cookie. Em chỉ khẳng định phạm vi đã có evidence; SCA và deployed gates chưa được coi là PASS.|Không nói liveSMTP/timing hoặc release đã đạt nếu chưa thực hiện|

## Chuẩn bị trước ghi hình

Chọn commit đã review, ghi source/JAR hash và environment. QĐ cung cấp fixture/credential qua kênh riêng, xác minh seed mới và rerun; không dùng account UI02/private helper như tài khoản demo chuẩn. Kiểm tra mạngCDN/SMTP, roles, category/shop active,12 products nhiều shop, ảnh, địa chỉ và shipping policy. Tắt preview nhạy cảm, không mở application secrets/terminalENV. Dùng password manager hoặc nhập ngoài khung ghi, không quay token/OTP. Chuẩn bị tab và bookmark theo route thật, không sửa URL tùy tiện trong video luồng chính. Không xóa hay reset tài khoản thật.

## Chuyển giao cho TD và QĐ

TD tiếp tục cart→checkoutCOD→OrderNEW→Vendorconfirm/ready. QĐ demo assignment→shipper→delivery/realtime/review; TD trình bày return/refund/revenue; QĐAdmin. HP chỉ giải thích boundary own address/catalog/inventory, không nhận implementation pricing/lifecycle. Nếu demo lỗi, dừng ghi, lưu reproduction an toàn và nói đúng limitation; không sửa DBstatus/tokenVersion để giả luồng thành công. Cuối video hiển thị repository, PRreview, các gate mở và trách nhiệm thành viên; video có mặt từng người do nhóm tự thực hiện.
