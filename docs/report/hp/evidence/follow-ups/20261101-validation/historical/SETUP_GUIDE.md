# Hướng dẫn setup UTEExpress phần HP

Source được mô tả: 6a26370659a2fa7b95c6a1d00d2598820de6bb34, ngày29/10/2026. Đây là runbook; DOC-01 không tuyên bố đã boot máy sạch, chạy SMTP thật hay hoàn thành REL-01.

## 1 Môi trường

JDK21 (JAVA_HOME và PATH cùng bản), Git, PostgreSQL; Maven Wrapper tải Maven3.9.11 lần đầu cần mạng. Docker Desktop chạy Linux containers nếu chạy postgres-it/Testcontainers; không bắt buộc Docker cho app dùng PostgreSQL đã cài. PostgreSQL test pin17.6; driver JDBC42.7.13 không phải server version. Bootstrap5.3.3 dùng CDN: máy demo cần mạng/CDN truy cập được, không hứa offline khi chưa đóng gói asset.

## 2 Chuẩn bị JDK và source

Cài JDK21 từ nhà cung cấp được trường/nhóm chọn. Trong PowerShell kiểm tra `java -version`, `$env:JAVA_HOME` và `.\mvnw.cmd -v` cùng Java21. Enforcer từ chối major khác. Clone repository vào thư mục riêng, checkout commit đã review; không reset working tree đang dùng. Lệnh Linux/macOS thay `.\mvnw.cmd` bằng `sh ./mvnw`.

## 3 Database riêng

DB admin tạo login riêng và database rỗng qua psql; không dùng production data. Ví dụ thao tác tương tác, không có mật khẩu trong script:

```sql
CREATE ROLE uteexpress_demo LOGIN;
\password uteexpress_demo
CREATE DATABASE uteexpress_demo OWNER uteexpress_demo;
```

Login này cần quyền tạo schema/table cho Flyway trong database demo riêng. Không mở quyền superuser cho app. Cấu hình tài khoản migration/runtime tách biệt cho production cần QĐ/HP review riêng. Không tự export SQL production; bản export/seed cuối phải do QĐ xác minh theo migrations và fixture.

## 4 Biến môi trường

Spring không tự nạp `.env`. Đặt ENV trong shell/IDE hoặc secret manager được phê duyệt; không commit giá trị. Ví dụ nhập tương tác:

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/uteexpress_demo'
$env:DB_USERNAME = 'uteexpress_demo'
$dbSecret = Read-Host 'DB password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
$env:MAIL_HOST = Read-Host 'SMTP host hoặc hộp thư test riêng'
$env:MAIL_PORT = '587'
$env:MAIL_FROM = Read-Host 'Sender được SMTP cho phép'
$env:MAIL_USERNAME = Read-Host 'SMTP username nếu yêu cầu'
$mailSecret = Read-Host 'SMTP password nếu yêu cầu' -AsSecureString
$env:MAIL_PASSWORD = [System.Net.NetworkCredential]::new('', $mailSecret).Password
$env:JWT_SECRET_BASE64 = Read-Host 'Base64 khóa ngẫu nhiên riêng ít nhất32 bytes'
$env:OTP_PEPPER_BASE64 = Read-Host 'Base64 pepper độc lập ít nhất32 bytes'
$env:UPLOAD_DIR = 'D:/uteexpress-demo-uploads'
```

Sinh mỗi secret bằng bộ sinh ngẫu nhiên mật mã được tổ chức cho phép; không copy test-only keys từ POM. Không in giá trị, commit hay đưa vào ảnh. MAIL_SMTP_AUTH/MAIL_STARTTLS mặc định true; chỉ điều chỉnh theo SMTP thật. Với hộp thư loopback test có thể đặt port của hộp thư, AUTH/STARTTLS false cho môi trường local cô lập; không dùng cấu hình đó cho production. Demo đăng ký/OTP cần hộp thư truy cập được; mock SMTP hoặc lỗi delivery không chứng minh email đã gửi.

SERVER_PORT mặc định8080. JWT cookie Secure mặc địnhtrue; `local` đặt false cho HTTP localhost, `prod` đặt true. Không dùng local làm nghiệm thu cookie HTTPS production. OTP TTL5phút, cooldown1phút, max5attempts; config không được nới vượt guard. UPLOAD_DIR phải là vùng lưu bền vững có ACL phù hợp, ngoài source và webroot; sao lưu cùng DB.

## 5 Build và migration

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
.\mvnw.cmd -Ppostgres-it clean verify
```

Đây là commands hướng dẫn, không là kết quả DOC-01 đã thực thi. test/package không thay PostgreSQL IT. Flyway tự chạy lúc app boot, schema uteexpress,28 migration ở snapshot, validate-on-migrate và out-of-order=false. Hibernate validate sau migration; không ddl-auto=update/create, không Flyway clean/baseline-on-migrate để né lỗi. Test profile loại persistence cho isolated HTTP; không dùng profile đó làm demo.

## 6 Chạy ứng dụng

```powershell
java -Duser.timezone=UTC -jar target/uteexpress-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Truy cập http://localhost:8080/ và /products; /api/v1/foundation phải FOUNDATION_READY; /actuator/health phải UP khi các health dependency đúng. Mail health có thể fail nếu SMTP chưa sẵn sàng; không sửa production config để che lỗi. Dừng Ctrl+C rồi xóa ENV nhạy cảm khỏi shell và đóng terminal.

## 7 Dữ liệu và tài khoản demo

Không có mật khẩu demo công khai dùng chung. Public registration chỉ tạo USER/PENDING; xác minh OTP mới ACTIVE. Role STAFF/VENDOR/Admin bootstrap phải theo fixture/handoff do QĐ/owner phê duyệt; không thêm SQL đổi role/password tùy tiện. ProductDemoSeeder chỉ hoạt động `demo & !prod` và `--uteexpress.demo.catalog.enabled=true`; cần sẵn shop APPROVED và category active được chọn theo slug. Seeder tạo12 active products qua2shop và các trạng thái ẩn/kiểm duyệt; không tạo đầy đủ order/shipping/finance. Tài khoản chủ shop thứ hai là DISABLED, không phải login demo. Không bật demo cùng prod. Full seed PR69 còn DRAFT tại lúc đối chiếu; phối hợp QĐ lấy artifact đã nghiệm thu, không coi file SEED_MANIFEST lịch sử là seed executable.

## 8 Smoke và lỗi thường gặp

Thiếu ENV/secret sai Base64: kiểm tra tên, không copy giá trị ra log. Connection refused/DB login fail: kiểm tra PostgreSQL, port, role và DB_URL. Lỗi timezone Asia/Saigon: chạy JVM UTC, không cần đổi timezone Windows. Hibernate validate/Flyway checksum mismatch: dừng, đối chiếu28 migration và schema, không sửa migration đã apply. Port8080 bận: chọn SERVER_PORT riêng thay vì kill Java không rõ owner. Login xong vẫn401 trên HTTP: kiểm tra dùng local cookie Secure phù hợp, tokenVersion/trạng thái account và cookie origin. POST403: tải lại form để có CSRF; không csrf.disable().429: chờ Retry-After và xem budget/socket peer. Image404: kiểm tra owner/visibility/storage/ACL, không expose UPLOAD_DIR static. Maven clean lỗi file lock Windows: dừng đúng JAR mình chạy hoặc copy JAR runtime vào vùng riêng trước test, không xóa dữ liệu uploads. Docker unavailable: báo postgres-it BLOCKED, không thay bằng H2.

## 9 Trước release

QĐ/HP hoàn tất SCA, SMTP/timing, TLS/proxy/trusted IP/cookie, ACL, off-host DB+uploads restore, load/restart/topology, seed/manual Ops và clean-host rehearsal. Ghi build SHA/JAR hash/environment/operator/expected/actual và raw evidence an toàn; không lấy unit PASS làm production acceptance. REL-01 do QĐ phụ trách, REL-02 HP/TD review theo Plan, chưa tag hoặc tạo release PR từ hướng dẫn này.
