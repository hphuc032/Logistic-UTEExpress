## Task ID

<!-- Ví dụ: QA-00 -->

## Owner

<!-- Người chịu trách nhiệm task -->

## Description

<!-- Vấn đề, thay đổi và kết quả mong đợi; liên kết task nếu có. -->

## Files changed

<!-- Liệt kê file/nhóm file và mục đích thay đổi. -->

## Test evidence

<!-- Ghi command, kết quả và log/report liên quan; ghi rõ lỗi hoặc chưa chạy. -->

- [ ] `./mvnw.cmd test` (Windows) hoặc `sh ./mvnw test` (Linux/macOS)
- [ ] `./mvnw.cmd package` (Windows) hoặc `sh ./mvnw package` (Linux/macOS)
- [ ] `git diff --check`

## Security impact

<!-- Nêu ảnh hưởng đến xác thực, phân quyền, dữ liệu nhạy cảm; ghi “Không” nếu không có. -->

## Database impact

<!-- Nêu thay đổi schema/migration/dữ liệu và cách triển khai nếu có; ghi “Không” nếu không có. -->

## Screenshots (nếu có UI)

<!-- Đính kèm ảnh trước/sau khi thay đổi UI; ghi “Không áp dụng” nếu không có UI. -->

## Review checklist

- [ ] Thay đổi đúng phạm vi task.
- [ ] Commit tuân thủ convention và có ý nghĩa.
- [ ] PR thông thường vào `develop`; CI phải pass và có review trước khi merge.
