# TASK COMPLETION REPORT — ADMIN-00

Owner: Quốc Đạt. Reviewer theo Master Plan: Hoàng Phúc.
Branch: `feature/admin-00-ops-foundation`.
Trạng thái: đã triển khai; cập nhật trên develop 81da6bd sau PR #8 AUTH-01. Chờ review của Hoàng Phúc trước khi merge ADMIN-00; không đánh dấu DONE toàn bộ DoD chỉ dựa vào kiểm thử cục bộ.

## Nền và dependency

- Develop tại thời điểm cập nhật: `81da6bd`, đã merge ORD-00 (gồm sửa subtotal), UI-01, SEC-01, DB-01, QA-00 và AUTH-01 qua PR #8.
- AUTH-01 gồm bản sửa `543cbe0`: tách insert user_roles để giữ optimistic version ban đầu. ADMIN-00 đã cập nhật nền này, không còn mang theo AUTH-01 chưa merge.
- Không nhập ORD-01 vì nhánh đó chưa có migration nghiệp vụ và chưa chạy được với Hibernate schema validation.
- Không sửa SecurityConfig, User/Role entity, Order/Payment hoặc các contracts ORD-00. Diff ADMIN-00 so với develop `81da6bd`.

## Implemented

- Hai mapping tĩnh GET /admin/dashboard và GET /manager/dashboard.
- Filter-chain SEC-01 kiểm tra role ở route; OpsDashboardService kiểm tra lại bằng method security.
- ADMIN không mặc nhiên vào /manager; muốn dùng cả hai cần cả hai role, giữ nguyên SEC-01.
- Dashboard theo vai trò dùng shared base/dashboard layout, sidebar, cards, empty state; không hiển thị số liệu giả, không mở các chức năng tương lai bằng link hỏng.
- AuditLog entity bất biến trong API ứng dụng; repository chỉ công khai saveAndFlush, không có API sửa/xóa.
- AuditLogService.append yêu cầu transaction đang tồn tại (MANDATORY). Audit và thao tác nghiệp vụ commit/rollback cùng nhau; lỗi lưu audit không được nuốt.
- Scalar actor_id tham chiếu users bằng FK thật, nullable cho tác vụ hệ thống đã được xác định. target_id là tham chiếu logic, không phải FK đa hình.
- Snapshot whitelist, mã action/target/reason do server định nghĩa. Không nhận nguyên HTTP payload hoặc serialize User entity.

## Files created

- src/main/java/com/uteexpress/governance/controller/OpsDashboardController.java
- src/main/java/com/uteexpress/governance/dto/OpsDashboardView.java
- src/main/java/com/uteexpress/governance/dto/AuditEntry.java
- src/main/java/com/uteexpress/governance/entity/AuditLog.java
- src/main/java/com/uteexpress/governance/repository/AuditLogRepository.java
- src/main/java/com/uteexpress/governance/service/OpsDashboardService.java
- src/main/java/com/uteexpress/governance/service/AuditLogService.java
- src/main/resources/templates/governance/dashboard.html
- src/main/resources/db/migration/V20260921081035__qd_admin00_audit_logs.sql
- src/test/java/com/uteexpress/governance/OpsDashboardTest.java
- src/test/java/com/uteexpress/governance/AuditLogServiceTest.java
- src/test/java/com/uteexpress/governance/AuditLogIT.java
- docs/ADMIN-00-completion-report.md
- docs/governance-audit.md
- docs/PROGRESS-2026-09-21.md

## Files modified

FoundationHttpTest, SecurityFoundationTest, UiLayoutTest và identity/controller/RegistrationControllerTest: mỗi test thêm mock AuditLogService vì HTTP test profile cố ý tắt persistence. Giữ nguyên các assertions; không mock audit trong PostgreSQL integration tests. OpsDashboardTest cũng mock UserRoleRepository giống các HTTP fixtures AUTH-01 mới; production persistence và security không bị tắt.

## Tests and build

JDK 21, Maven Wrapper, Docker/PostgreSQL 17.6.

- `mvnw.cmd -B -ntp -Ppostgres-it verify` trên develop 81da6bd + ADMIN-00: **BUILD SUCCESS, tổng 98 tests** (87 unit/HTTP và 11 integration), không fail/error/skip. Verify bao gồm test, package và integration test; jar đã được tạo.
- Log đầy đủ cục bộ: target/admin00-updated-verify.log. Kết quả FAIL ở lần chạy nền e4a1101 là lịch sử trước bản sửa AUTH-01, không phải kết quả hiện tại.
- Riêng ADMIN-00: 14 tests dashboard/quyền, 9 tests validation audit và 7 PostgreSQL audit tests đều đạt.
- PostgreSQL: chạy mới cả baseline + identity + audit migrations; validate và migrate lại không còn pending; actor FK, target CHECK, null system actor, commit/rollback, missing-transaction đều được kiểm tra.
- `scripts/Test-DatabasePlan.ps1`: PASS 30 bảng kế hoạch, không chu trình.
- `git diff --check`: PASS.
- UI render từ MockMvc thật, chụp bằng Edge headless tại 375/768/1366/1440 px cho cả hai role. Không tràn ngang; Bootstrap tải được; một main landmark. Ảnh và HTML nằm ở target/ui-preview. Đây là QA giao diện render, không phải login end-to-end.

Ảnh minh họa được lưu tại docs/screenshots/admin00-admin-desktop.png và docs/screenshots/admin00-manager-mobile.png (giao diện không đổi trong lần cập nhật nền AUTH-01).

### Lịch sử lỗi AUTH-01 (đã có bản sửa trên develop)

`AuthRegistrationIT.registrationPersistsPendingUserWithBcryptAndOnlyDefaultUserRole`, dòng 82: kỳ vọng version = 0 nhưng thực tế = 1.

Đã tái hiện trên checkout nguyên bản AUTH-01 e4a1101, không có bất cứ code ADMIN-00 nào, bằng:

```powershell
.\mvnw.cmd -B -ntp -Dtest=RegistrationServiceTest -Dit.test=AuthRegistrationIT -Ppostgres-it verify
```

Kết quả nguyên bản: cùng một assertion thất bại. Log nằm ở ../review-auth01/auth01-verification.log và ../review-auth01/target/failsafe-reports.

HP đã sửa tại 543cbe0 và merge trong PR #8. ADMIN-00 không thay UserEntity hoặc nới assertion để làm xanh; token_version = 0 và version = 0 vẫn được kiểm tra trên nền mới.

## Security checked

- Guest: 401; USER/VENDOR/SHIPPER và role sai: 403; quyền được kiểm tra cả khi gọi service trực tiếp.
- Giữ CSRF và security filter hiện có; không thêm tài khoản demo, mật khẩu hoặc chế độ bỏ qua xác thực.
- Không có endpoint nhận lệnh ghi/xóa nhật ký từ browser.
- actorId trong AuditEntry chỉ dành cho caller tin cậy sau khi resolve identity; không parse CurrentUser.subject thành Long.
- Reason ở ADMIN-00 là mã do server định nghĩa, không phải ô nhập văn bản tự do.
- Append-only ở lớp ứng dụng, không tuyên bố chống sửa trực tiếp bằng tài khoản quản trị DB.

## Remaining issues / Next steps

1. AUTH-01 đã merge; ADMIN-00 đã cập nhật develop. Giữ migration audit sau migration users.
2. HP review PR ADMIN-00 về develop theo quy trình nhóm. Chưa approve/merge ADMIN-00.
3. Login thực tế chờ AUTH-02; không thể dùng browser đăng nhập Admin/Manager ở giai đoạn này. Dashboard đã được kiểm thử bằng principal test qua security chain thật.
4. Audit viewer/filter/aggregate thuộc DASH-02; quản trị account/category/shipping thuộc task riêng. Chưa triển khai ngoài ADMIN-00.
5. Sau khi ADMIN-00 tích hợp: ADMIN-04 (Category), rồi SHIP-00 (Provider/rates). ADMIN-03 chờ VENDOR-01.

Suggested commit: `feat(governance): implement ADMIN-00 ops dashboards and audit foundation`
