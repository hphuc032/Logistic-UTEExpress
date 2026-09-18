# UTEExpress

Foundation cho task **ARCH-01**, owner **Hoàng Phúc**, sprint **S0**, priority **P0**.
Nguồn phạm vi: `UTEExpress_Master_Project_Plan.docx` (A7, M2–M4, MASTER TASK BOARD).

## Yêu cầu và chạy local

- JDK **21**; đặt `JAVA_HOME` tới JDK 21.
- Dùng Maven Wrapper để luôn chạy Maven **3.9.11**. Lần đầu cần Internet để tải Maven và dependencies.
- Spring Boot **4.1.1** được khóa trong `pom.xml`.
- Foundation không yêu cầu PostgreSQL, Docker, SMTP hay secrets.

Windows PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/uteexpress-0.1.0-SNAPSHOT.jar
```

Linux/macOS (script giữ LF; có thể chạy qua `sh` ngay sau checkout):

```sh
sh ./mvnw test
sh ./mvnw package
java -jar target/uteexpress-0.1.0-SNAPSHOT.jar
```

Nếu Maven đã cài đúng **3.9.11**, có thể dùng `mvn test` và `mvn package`.
Dừng ứng dụng bằng Ctrl+C. Đổi cổng bằng biến `SERVER_PORT` hoặc `--server.port=8081`.

- `GET http://localhost:8080/actuator/health` → HTTP 200, trường `status` là `UP`.
- `GET http://localhost:8080/api/v1/foundation` → `{"application":"UTEExpress","status":"FOUNDATION_READY"}`.
- `/` chưa có giao diện; trang chủ và shared layout thuộc UI-01/PROD-01.
- Đây là foundation chưa có authentication. Security contract và bảo vệ endpoint thuộc SEC-01.

## Cấu trúc và tài liệu

- `src/main/java/com/uteexpress`: bootstrap, các module nghiệp vụ và `common`.
- `src/main/resources/application.yml`: cấu hình chung, không secret.
- `src/test/java/com/uteexpress`: HTTP contract và architecture tests.
- [Quy ước kiến trúc, DTO, exception và version](docs/architecture.md).
- [Task Completion Report](docs/ARCH-01-completion-report.md).
- `docs/dependencies.txt`: dependency tree đã resolve để review baseline.

Không có JPA entity, migration hay repository giả trong ARCH-01. DB-01 sẽ thêm JPA,
PostgreSQL, Flyway và cấu hình môi trường; không thay PostgreSQL bằng H2.
Authentication/JWT/OTP, Cart, Order, Admin, WebSocket và CI thuộc các task riêng.

## Kiểm tra trước khi bàn giao

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
```

Surefire reports ở `target/surefire-reports`; executable JAR ở `target/`.
Không commit `target`, `.work`, `.env`, credentials hoặc thư mục upload.
Branch của task: `feature/arch-01-foundation`. Review/merge develop thực hiện riêng.
