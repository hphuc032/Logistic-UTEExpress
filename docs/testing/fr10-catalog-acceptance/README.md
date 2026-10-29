# FR-10 Catalog — browser evidence và human acceptance

**Technical FAIL / HUMAN PENDING**. Đọc [report](report.md), [finding](findings.md), [matrix](coverage-matrix.md) và [checklist cho HP](human-acceptance-checklist.md).

## Scope đã chốt

Prompt ban đầu dùng FR-10 như Human Acceptance rộng. Master Plan gốc định nghĩa FR-10 là **Catalog: Home, category, product/shop detail**, Public/P0, HP owner, TD reviewer, dependency FR-09. HP trả lời trực tiếp: **“FR-10 Catalog theo Master Plan”**. Packet dùng phạm vi đó, không gán lại mã requirement cho shipping/realtime hoặc seed gate. [Dependency matrix](dependency-matrix.md) phân biệt hai namespace FR06/FR07.

Source thực thi `a5620f1971d3b684d9b05d7444f5e90442404656`, tree `797bf319420e7a82f5d2ff89859f998ae9117b35`; merge #75 đã nằm trên develop. Không dùng source G02 `5089a8e` hoặc PR69 publication `c5ce35c` làm build acceptance này. PR69 không bị sửa. [Source proof](source-proof.json), [GitHub snapshot](github-snapshot.json).

## Kết quả và cách đọc

- Baseline54:52PASS/2FAIL harness; menu recheck3:3PASS; diagnostic empty-state12:12FAIL ứng dụng.
- Final66 unique viewport-case IDs:54PASS/12FAIL; raw69 executions có lặp, không cộng làm distinct tests.
- 30 Java unit/MVC/architecture và7 shared-UI Node PASS, không là full suite hoặc PostgreSQL IT suite.
- Ba run dùng container/database/app riêng,28 SQL migrations, schema validate;34 table fingerprints trước/sau bằng nhau trong từng run. Không Order/Payment, không shared-seed acceptance.
- Screenshot là Chrome headless thật; fixture ảnh74bytes và dữ liệu Catalog là synthetic test data, không phải sản phẩm/khách thật. Không sửa screenshot hay dùng screenshot do AI dựng.
- Human checklist36 dòng để trống actual/result/reviewer/time/signature. Automation và agent xem ảnh không là HP manual PASS.

## Reproduction từ packet đã publish

Không chạy runner trên checkout/index chính hoặc DB dùng chung. Dùng một linked worktree mới, clean, detach đúng source a5620f1. Copy thư mục `runner/` của packet vào một thư mục private bên ngoài worktree trước khi checkout source; không thêm helper vào production classpath. Trên Windows ví dụ:

```powershell
git worktree add --detach <NEW-EMPTY-WORKTREE> a5620f1971d3b684d9b05d7444f5e90442404656
# Trong NEW-EMPTY-WORKTREE, chỉ build artifact; command này không chạy test.
$env:JAVA_HOME = '<JDK21>'
$env:MAVEN_OPTS = '-Duser.timezone=UTC'
.\mvnw.cmd -B --no-transfer-progress -DskipTests package
python <PRIVATE-RUNNER>/run_catalog_acceptance.py --root <NEW-EMPTY-WORKTREE> --output <NEW-OUTPUT-DIRECTORY> --java <JDK21>/bin/java.exe --node <NODE>/node.exe --playwright <EXISTING-PLAYWRIGHT-MODULE> --chrome <EXISTING-CHROME>/chrome.exe --browser-runner <PRIVATE-RUNNER>/catalog-browser.cjs
```

Các token `<...>` phải thay bằng đường dẫn máy thực, không paste nguyên. Output directory phải chưa tồn tại. Runner hiện tại có22 group×3=66 unique checks; dự kiến66 canonical checks có12FAIL cho bug chưa sửa, khác lịch sử baseline18 group + targeted runs được lưu nguyên. `--case-filter CAT-KEYBOARD-MENU` chỉ recheck3; `--case-filter CAT-POPULATED` chỉ diagnostic12. Source SHA guard sẽ từ chối checkout publication commit; không reset để né guard.

Docker Desktop/PG17.6 và Chrome/Playwright phải sẵn có. Không cài dependency mới tự động. Runner tạo tên container UUID, bind database và app127.0.0.1, dùng secret sinh trong memory và giữ raw config/log ngoài repo. Chỉ xóa container chính nó tạo. Không dùng `prune`, sửa migration hoặc force state Order/Payment. Nếu Docker/browser/CDN không chạy, giữ lỗi và ghi BLOCKED, không chuyển sang HTTP giả làm browser PASS.

Runner hiện đóng app/container sau execution; checklist HP cần một fresh owned session do operator chuẩn bị theo cùng fixture/source rồi giữ mở có kiểm soát trong lúc HP thao tác. Không dùng URL port cũ sau cleanup. Không bật lại database dùng chung. Việc chuẩn bị một session mới chưa phải human acceptance. Không có credential demo công khai; Catalog chạy Guest.

## Evidence

`attempts/` giữ nguyên ba kết quả, receipt, dataset, DB fingerprints và69 PNG (có lặp viewport-case). `prior-runners/` giữ hai version cũ; bản cuối ở`runner/`. [Artifact manifest](artifact-manifest.json) ghi hash từng file; tự manifest không tự hash. Original results không được ghi lại thành PASS. CI nếu xanh vẫn không đóng FR10-CAT-01 hoặc human acceptance.


## Final dependency read-back

During publication prep, PR69 advanced from c5ce35c to docs-only3ba76d3. Latest additive record states FR06 ACCEPTED WITH DOCUMENTED LIMITATIONS, TD raw verification PASS per supplied decision (not TD seed execution), and FR07 AUTHORIZED / NOT RUN, TD owner/QĐ support only. Direct HP decision and TD raw-review URL/hash remain pending traceability. This read-back preserves the original execution-time PENDING labels and all original attempts. It neither reruns those gates nor expands their acceptance. See [latest dependency observation](latest-dependency-observation.json). Develop and Catalog tested source remain a5620f1.
