# ARCH-01 Architecture and conventions

## Phạm vi và quyết định

UTEExpress là một Spring Boot modular monolith, chia package theo nghiệp vụ,
với kiến trúc Controller → Service → Repository bên trong mỗi module.
ARCH-01 chỉ dựng foundation; các nhiệm vụ trong tài liệu tổng thể không tự động
trở thành phạm vi của task này. Không triển khai SEC-01, UI-01, DB-01, ORD-00 hay QA-00.

Workspace ban đầu chỉ có file Word, không có source hoặc Git repository.
Git được khởi tạo với HEAD ở `feature/arch-01-foundation`; chưa có base commit/develop/remote.
Không tạo lịch sử develop giả, không tự commit hoặc merge.

## Package và ba tầng

```text
com.uteexpress
├── UteExpressApplication
├── identity
├── account
├── catalog
├── shop
├── engagement
├── cart
├── checkout
├── order
├── payment
├── promotion
├── shipping
├── notification
├── governance
├── security                 # dành cho SEC-01
└── common
    ├── controller           # endpoint mô tả foundation
    ├── service
    ├── dto
    ├── exception
    ├── config
    ├── validation
    └── storage
```

Mỗi module nghiệp vụ từ `identity` đến `governance` có các package
`controller`, `service`, `repository`, `entity`, `dto`, được giữ trong Git bằng
`package-info.java`. Chúng chưa chứa implementation.

| Thành phần | Trách nhiệm | Ranh giới |
| --- | --- | --- |
| Controller | HTTP, binding DTO, `@Valid`, gọi service, trả view/response | Không truy cập repository hoặc entity |
| Service | Nghiệp vụ, transaction, ownership, điều phối | Không phụ thuộc controller, Servlet hay HTTP request |
| Repository | Query và persistence | Không gọi ngược service/controller |
| Entity | Mô hình persistence nội bộ module | Không bind form hoặc trả trực tiếp ra API |
| DTO | Hợp đồng request/response/command | Không phụ thuộc entity, repository, service, controller |
| Common | Hạ tầng dùng chung | Không phụ thuộc module nghiệp vụ |

Module khác chỉ dùng service/DTO contract công khai của owner, không truy cập entity
hoặc repository của nhau. Không tạo vòng phụ thuộc giữa module. Dùng constructor
injection. Chỉ tách interface service khi thực sự có contract hoặc nhiều implementation;
không tạo cặp `XService`/`XServiceImpl` rỗng hàng loạt.

Endpoint foundation đi qua Controller → Service và trả DTO. Nó không có dữ liệu cần
lưu nên không tạo repository giả. Tầng persistence được hiện thực cùng task nghiệp vụ
và DB-01. ArchUnit kiểm tra dependency tầng, DTO, truy cập liên module và chu trình;
quy tắc repository cho phép rỗng tới khi có implementation.

## DTO convention

- Tên thể hiện mục đích: `XCreateRequest`, `XUpdateRequest`, `XResponse`/`XView`,
  `XCommand`, `XSearchCriteria`; đặt trong module sở hữu.
- JSON request/response ưu tiên Java record bất biến; MVC form có thể dùng bean có
  constructor/accessor phù hợp binding. Không dùng entity làm request.
- Whitelist đúng trường người dùng được sửa. Không nhận `ownerId`, `role`, `paidAmount`,
  `commissionRate` như nguồn quyết định; server/principal/service quyết định các giá trị đó.
- JSON có field ngoài DTO bị từ chối bằng `fail-on-unknown-properties=true`.
  MVC form sau này cần allowlist riêng qua binder; setting JSON không bảo vệ form binding.
- Request dùng Jakarta Bean Validation và controller dùng `@Valid`. Kiểm tra trạng thái,
  ownership, tính toán và transaction nằm trong service.
- Mapping rõ ràng tại biên module; không thêm MapStruct/Lombok khi chưa có nhu cầu.
- ID dùng `Long` theo quy ước BIGINT của kế hoạch; tiền dùng `BigDecimal`, không `double`.
  Thời điểm kỹ thuật dùng `Instant`/UTC; hiển thị theo `Asia/Ho_Chi_Minh` ở UI.
- Response thành công trả DTO trực tiếp, dùng HTTP status phù hợp; không bọc một generic
  success envelope khi chưa cần. Pagination sẽ dùng DTO riêng, không expose JPA Page/entity.

## Exception convention

`ApplicationException(ErrorCode)` dùng cho lỗi dự kiến. Danh mục `ErrorCode` quyết định
HTTP status và public message; không truyền exception/SQL/message tùy ý ra client.
Module bổ sung code nghiệp vụ khi cần, đồng bộ với owner/consumer.

| Code | HTTP | Trường hợp |
| --- | --- | --- |
| INVALID_REQUEST | 400 hoặc status lỗi HTTP gốc | JSON sai, thiếu body, binding/protocol |
| VALIDATION_FAILED | 400 | DTO không đạt Bean Validation |
| RESOURCE_NOT_FOUND | 404 | Tài nguyên/route không tồn tại |
| CONFLICT | 409 | Xung đột trạng thái nghiệp vụ |
| INTERNAL_ERROR | 500 hoặc status 5xx gốc | Lỗi chưa dự kiến |

```json
{
  "timestamp": "2026-09-18T14:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/example",
  "errors": [{"field": "name", "message": "Invalid value."}]
}
```

`GlobalExceptionHandler` dùng `ResponseEntityExceptionHandler` để giữ status và header
của Spring MVC (đặc biệt 405/Allow, 415). Lỗi validation không trả rejected value;
public message hiện dùng câu cố định để không lộ đầu vào. Lỗi bất ngờ chỉ log tên class,
không log payload/message có thể chứa credential. Không có stacktrace/SQL trong response,
`path` không kèm query string. Các lỗi phát sinh trước MVC hoặc ở servlet container dùng
fallback của Spring Boot; cấu hình vẫn tắt message, exception, binding details và stacktrace.
Đây là convention JSON cho foundation. Form `BindingResult`, trang lỗi HTML và thông báo
cụ thể theo trường được triển khai với UI-01/các module; lỗi Security Filter Chain thuộc SEC-01.

## Version và dependency lock

| Thành phần | Version | Cách khóa |
| --- | --- | --- |
| Java | 21 | compiler release và Enforcer `[21,22)` |
| Spring Boot | 4.1.1 | parent POM cố định, BOM quản lý dependency transitive |
| Maven | 3.9.11 | Wrapper URL + SHA-256; Enforcer yêu cầu đúng version |
| Maven Wrapper | 3.3.4 | plugin và generated scripts |
| Maven Enforcer | 3.6.2 | explicit plugin version |
| Maven Site | 3.12.1 | pluginManagement cố định cho lifecycle site |
| ArchUnit | 1.4.1 | explicit test dependency |

Các plugin compiler/resources/surefire/jar/Boot được khóa qua parent POM cố định.
`requirePluginVersions` chặn plugin lifecycle không có version;
`requireReleaseDeps` chặn dependency SNAPSHOT. Không dùng LATEST/RELEASE/version range
trong khai báo dependency, không bật snapshot repositories. Version của ứng dụng có thể
là `0.1.0-SNAPSHOT`; đó không phải một dependency động.

Dependency đang dùng: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`,
`spring-boot-starter-thymeleaf`, `spring-boot-starter-actuator`; test dùng
`spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `archunit-junit5`.
Không thêm Security/JWT/Mail/WebSocket trước task tương ứng.

DB-01 sẽ thêm `spring-boot-starter-data-jpa`, `postgresql`, Flyway core/PostgreSQL
bằng version do cùng BOM quản lý, cùng profiles dùng ENV và `ddl-auto=validate`.
Không có datasource/migration bị vô hiệu hóa âm thầm trong foundation. Test PostgreSQL
và Testcontainers chỉ thêm khi có persistence cần kiểm tra. Không có H2.

`docs/dependencies.txt` ghi dependency tree đã resolve. Đây là ảnh chụp phục vụ review;
Maven lấy version từ POM/BOM, không đọc file này như lockfile. Mỗi lần nâng dependency
phải sửa version có chủ đích, chạy test/package/boot và cập nhật tree.
Wrapper ZIP đã được đối chiếu SHA-512 do Maven Central cung cấp trước khi lưu SHA-256.

Nguồn kiểm tra:
- https://start.spring.io/metadata/client (stable 4.1.1 khi kiểm tra ngày 2026-09-18)
- https://docs.spring.io/spring-boot/4.1/
- https://maven.apache.org/tools/wrapper/

## Cấu hình và nhiệm vụ tiếp theo

`application.yml` là cấu hình chung, không secret; chỉ expose Actuator health không có
chi tiết nội bộ. Cổng mặc định 8080 và có thể override bằng `SERVER_PORT`.
Chưa cần profile local/test/prod hoặc `.env.example` chứa các biến chưa được sử dụng.
Khi DB-01/SEC-01/AUTH triển khai, thêm ENV và profiles đúng phần M4 của kế hoạch;
Spring Boot không tự đọc `.env` nếu không có cơ chế nạp tương ứng.

Security, Bootstrap/shared UI, schema/Flyway, Order contracts và CI đều để task riêng.
ARCH-01 không chứng minh các tiêu chí acceptance của authentication hoặc persistence.
