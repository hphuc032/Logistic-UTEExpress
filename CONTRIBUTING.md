# Đóng góp cho UTEExpress

## Git workflow

1. Cập nhật `develop` và tạo task branch từ đó:

   ```sh
   git switch develop
   git pull --ff-only origin develop
   git switch -c chore/qa-00-ci-workflow
   ```

   Thay tên branch theo task, ví dụ `feature/<task-id>-<description>`,
   `fix/<task-id>-<description>` hoặc `chore/<task-id>-<description>`.
2. Làm việc trên task branch, giữ thay đổi trong phạm vi task được giao.
3. Chạy kiểm tra local, xem diff và commit các thay đổi có ý nghĩa theo convention bên dưới.
4. Push task branch: `git push -u origin <task-branch>`.
5. Tạo Pull Request vào `develop`, điền đầy đủ PR template và bằng chứng test.
6. Chờ review, xử lý góp ý và bảo đảm CI pass trước khi merge.

Không commit trực tiếp vào `main`. Không tạo fake/meaningless commits,
commit rỗng hoặc thay đổi vô nghĩa chỉ để tạo hoạt động hay kích hoạt CI.
Không commit secrets, `.env`, `target/` hoặc công cụ local.

## Commit convention

Định dạng: `<type>: <task-id> <mô tả ngắn, cụ thể>`.

| Prefix | Mục đích |
| --- | --- |
| `feat:` | Thêm tính năng |
| `fix:` | Sửa lỗi |
| `refactor:` | Cải tổ code, giữ nguyên hành vi |
| `test:` | Thêm hoặc sửa kiểm thử |
| `docs:` | Thêm hoặc sửa tài liệu |
| `chore:` | Công việc bảo trì, cấu hình build hoặc CI |

Ví dụ: `chore: QA-00 add CI workflow and contribution guidelines`.

## Kiểm tra trước khi gửi PR

Dùng JDK **21** và đặt `JAVA_HOME` tới JDK 21 trong môi trường chạy lệnh.
Maven Wrapper hiện khóa Maven **3.9.11**; không tự nâng Maven, Spring Boot
hay dependency để xử lý lỗi của task khác.

Windows PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
git diff --check
git status
```

Linux/macOS:

```sh
sh ./mvnw test
sh ./mvnw package
git diff --check
git status
```

Test reports nằm ở `target/surefire-reports/`, JAR ở `target/`.
Nếu test/build lỗi ngoài phạm vi task, ghi rõ command, lỗi và file/module liên quan
trong PR; không sửa business code ngoài phạm vi chỉ để test pass.

## CI và review

Workflow `.github/workflows/ci.yml` chỉ chạy khi có Pull Request nhắm tới
`develop` hoặc `main`. Job `Maven test and package` checkout source, thiết lập
Temurin Java 21 rồi chạy `sh ./mvnw --batch-mode --no-transfer-progress test`
và `sh ./mvnw --batch-mode --no-transfer-progress package`, không bỏ qua test.

PR phải có review và CI thành công trước khi merge. Để GitHub bắt buộc quy tắc
này, maintainer cần thiết lập branch protection/ruleset cho `develop` và `main`,
yêu cầu PR, approval và status check `Maven test and package`.
Workflow trong repository không tự bật các thiết lập bảo vệ branch này.
