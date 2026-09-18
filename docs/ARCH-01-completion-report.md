# ARCH-01 Task Completion Report

**Trạng thái:** Hoàn thành implementation và kiểm tra local; sẵn sàng review.

| Thuộc tính | Giá trị |
| --- | --- |
| Project | UTEExpress |
| Task | ARCH-01 Foundation |
| Owner | Hoàng Phúc |
| Reviewer theo kế hoạch | Tiến Đạt |
| Sprint / Priority | S0 / P0 |
| Branch | feature/arch-01-foundation |
| Ngày kiểm tra | 2026-09-18, Asia/Ho_Chi_Minh |
| Commit / PR / Merge | Chưa thực hiện; không merge develop |

## Hiện trạng ban đầu

Workspace chỉ có `UTEExpress_Master_Project_Plan.docx`, chưa có source, Maven project,
Git repository, remote hoặc branch develop. Đã đọc kế hoạch và phân biệt phạm vi ARCH-01
với yêu cầu của các task khác. File Word giữ nguyên. Git mới được khởi tạo trên branch yêu cầu;
không thể tuyên bố branch được tách từ develop vì develop chưa tồn tại.

## Kết quả

| Yêu cầu | Kết quả |
| --- | --- |
| Maven Wrapper | Script Windows và Unix; Wrapper 3.3.4, Maven 3.9.11, SHA-256 khóa distribution |
| Spring Boot chuẩn | Maven layout, Java 21, Boot 4.1.1, application.yml, executable JAR |
| Package structure | Đủ 15 package định hướng; 13 module nghiệp vụ có controller/service/repository/entity/dto |
| Kiến trúc ba tầng | Ranh giới và quy tắc dependency được tài liệu hóa, kiểm tra bằng ArchUnit |
| DTO convention | DTO record, whitelist, validation, từ chối JSON field không khai báo, không expose entity |
| Exception convention | ErrorCode, ApplicationException, ErrorResponse, GlobalExceptionHandler |
| Version/dependency lock | Parent/BOM cố định, Enforcer, version plugin/test dependency, resolved dependency tree |
| Application boot | JAR chạy Tomcat và trả HTTP đúng, không cần database/external service |

Tầng repository/entity là package skeleton; endpoint foundation chỉ cần controller/service/DTO
vì không có dữ liệu phải lưu. Không tạo implementation nghiệp vụ hoặc repository giả.

## File bàn giao

- Build/tooling: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`,
  `.gitignore`, `.gitattributes`, `.editorconfig`.
- Bootstrap: `src/main/java/com/uteexpress/UteExpressApplication.java`.
- Foundation: `common/controller/FoundationController.java`, `common/service/FoundationService.java`,
  `common/dto/FoundationResponse.java` (dưới `src/main/java/com/uteexpress`).
- Error contract: `common/dto/ErrorResponse.java`, `common/exception/ErrorCode.java`,
  `common/exception/ApplicationException.java`, `common/exception/GlobalExceptionHandler.java`.
- Package skeleton: 83 file `package-info.java`; tổng cộng 91 Java source ở main.
- Configuration: `src/main/resources/application.yml`.
- Tests: `src/test/java/com/uteexpress/FoundationHttpTest.java`, `ArchitectureTest.java`.
- Tài liệu: `README.md`, `docs/architecture.md`, `docs/dependencies.txt`, báo cáo này.

Không sửa file project cũ. File tạo mới chưa commit/stage để người dùng review.

## Xác minh

| Kiểm tra | Kết quả |
| --- | --- |
| `mvn -B -ntp test` | PASS, 20 tests, 0 failures/errors/skips |
| `mvn -B -ntp package` | PASS, executable JAR được tạo |
| `.\mvnw.cmd -version` | Maven 3.9.11, Java 21 |
| `.\mvnw.cmd -B -ntp clean test package` sau chỉnh cấu hình cuối | PASS, 20 tests, 0 failures/errors/skips |
| Chạy `java -jar ... --server.port=0 --server.address=127.0.0.1` | PASS, boot từ JAR thực tế |
| `GET /actuator/health` | 200, status UP |
| `GET /api/v1/foundation` | 200, application UTEExpress, status FOUNDATION_READY |
| `GET /unknown-route` | 404, RESOURCE_NOT_FOUND |
| `POST /api/v1/foundation` | 405, giữ Allow: GET |
| `GET /test-fixtures/validation` trên JAR | 404; fixture không được đóng gói vào main |
| Kiểm tra whitespace/conflict marker toàn bộ file text mới | PASS |
| Git HEAD | feature/arch-01-foundation; chưa có commit/remote/merge |

Test HTTP gồm boot, health, actuator exposure, DTO hợp lệ, Bean Validation, JSON field lạ,
JSON sai, body thiếu, 415, lỗi nghiệp vụ 404/409, route 404, method 405 và che lỗi nội bộ/query string.
6 architecture rules kiểm tra ranh giới tầng, DTO, truy cập liên module và cycle.

Lần smoke test cuối: **2026-09-18 21:31:49 +07:00**, localhost port **59106**.
Đã dừng process sau kiểm tra, không để server chạy nền.

JAR: `target/uteexpress-0.1.0-SNAPSHOT.jar`.
SHA-256: `eb54628dd6d6ddeb02a189e293d901b188da12d1ddb588e921b45d231d7e1ab9`.
Bằng chứng local (được ignore, không commit): `target/surefire-reports/`,
`.work/final-build.log`, `.work/boot-final.log`, `.work/boot-smoke.json`.

## Giới hạn và bàn giao

- Không triển khai Authentication, JWT, OTP, Cart, Order, Admin, WebSocket hay entity đầy đủ.
- Security thuộc SEC-01; HTML/shared layout thuộc UI-01; persistence/migration thuộc DB-01;
  order contracts thuộc ORD-00; CI/PR workflow thuộc QA-00.
- JPA/PostgreSQL/Flyway chưa thêm. Không kết luận test persistence, role/ownership hoặc E2E nghiệp vụ đã đạt.
- Đã test Windows/Java 21; Unix wrapper được cung cấp từ Apache nhưng chưa chạy trên Linux/macOS.
- Test phát warning về Mockito tự attach agent trên Java 21; không ảnh hưởng kết quả. Cấu hình agent
  cho JDK khác có thể bổ sung trong QA-00. Test lỗi bất ngờ cố ý sinh một log ERROR.
- DoD về PR/reviewer/merge chưa thực hiện; yêu cầu hiện tại chỉ đề xuất commit và cấm merge develop.
- Git do sandbox tạo ban đầu được giữ nguyên bản sao tại `.work/git-sandbox-backup`, sau đó repo rỗng
  được tạo lại dưới tài khoản người dùng để tránh lỗi ownership; không thay đổi Git config toàn cục.

Commit message đề xuất:

```text
feat(arch): implement ARCH-01 project foundation
```
