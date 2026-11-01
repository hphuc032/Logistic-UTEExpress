# DOC-01 current validation ngày 01/11/2026

**READY FOR HUMAN REVIEW — PR76 vẫn DRAFT, chưa approve/merge hoặc final acceptance.**
Phần cập nhật này thay thế các nhãn missing/render blocker của snapshot29/10 bên dưới; không sửa hoặc relabel kết quả lịch sử.

## Current source và nội dung

- Original HEAD `7dc3a885a4358c212fd77f93434dcad22fd1243a`; accepted develop mới `a83c4aa03fb3806bd5aba47f0f9fa5ccf0f0b473`; sync bằng merge thường, không conflict/rewrite/force-push.
- Master Plan gốc mở lại được và hash `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a` không đổi. Năm chương1/2/4/5/12, HP owner/QĐ reviewer, dependency QA-01 giữ đúng Plan.
- [Source audit](follow-ups/20261101-validation/source-validation.json) kiểm tra19 mục: controller/entry/service path, anchor/line, hash và test references. **19 source-excerpt entries không đổi whole Git blob** giữa source cũ/mới; `app.js` historical hash dùng CRLF checkout bytes đã kiểm tra riêng, không thay hash để che khác biệt.
- Rà modular monolith/ba tầng/DTO/persistence; registration/JWT/logout/OTP/reset/CSRF; profile/avatar/address/default; shop/product/image ownership, catalog visibility/search/sort/page/inventory; team/Git. Làm rõ `AccountIdentityService.changePassword → UserEntity.changePassword → resetPassword`; cập nhật navigation/source context và actual solo governance (approval0, conversation resolution ON, required CI/strict/Admin/no-force/no-delete giữ nguyên). DOC01 chỉ đọc settings, không đổi policy.
- Shop profile/logo/banner editing và highestRated vẫn chưa có flow; không dựng chức năng từ tên dự kiến. Không coi draft shared seed hoặc PAY03 trên branch khác đã nằm trong source được chấp nhận. Không gán independent PostgreSQL execution cho QĐ hoặc đóng QA/release.
- Source index/setup/demo có addendum nguồn mới. [Original130 hashes](follow-ups/20261101-validation/original-publication-hashes.json) và các commit cũ giữ nguyên; các file cập nhật có [bản lịch sử nguyên byte](follow-ups/20261101-validation/historical/). DOCX gốc còn tại original commit. Hình/source/test receipts cũ giữ source/operator riêng.

## 14 ảnh bổ sung thực tế

[Attempt3 provenance](follow-ups/20261101-validation/attempt-3/new-screenshots.json) ghi route/role/viewport/source/JAR/browser/time/hash và limits cho14 mục4.3–4.8,5.1–5.7,5.9. [Checklist missing](missing-screenshots.json) hiện không còn mục thiếu file hình; **không đồng nghĩa toàn bộ functional/human acceptance PASS**.

- Java21 dựng clean detached source `a83c4aa` bằng `mvnw.cmd -DskipTests package`: **BUILD PASS, tests skipped**, không ghi là test PASS.
- JAR SHA256 `7392d65248ea6f62585a1e21d1a36cad3bb0d4178fbb18daea56dd687100a66b`.
- Chrome thật,1280×900, PostgreSQL17.6/Mailpit loopback riêng; anonymous/USER/VENDOR; dữ liệu hư cấu, không truy cập Render/production.
- Quan sát login→JWT-restored protected profile, logout→login/401, resend POST200, generic forgot redirect, reset form, protected401/missing-CSRF403, avatar upload/read, first address/default, shop form, Vendor list, product image upload/read và catalog query. Form/reset/list không chứng minh CRUD/reset full journey; từng record ghi rõ giới hạn.
- [Attempt1/2](follow-ups/20261101-validation/attempt-notes.md) giữ BLOCKED: runner ghép sai absolute redirect, rồi expectation302 sai với resend view200. Giữ10 partial screenshots, không sửa ứng dụng hoặc nới assertion; attempt3 dùng đúng contract và chụp14/14.
- Có **29 genuine screenshot files**:5 lịch sử +24 files qua3attempts; DOCX chọn **19 UI illustrations** (5 cũ+14 mới). Không gọi29 là29 độc lập functional tests.
- Mỗi attempt chạy28 migrations và Hibernate validate khi boot DB riêng. Đây **không phải full postgres-it**. Sáu owned containers/ba JVMs đã dọn đúngID và xác nhận absent. Fixture avatar/address/image/OTP/logout mutation có chủ đích; không tuyên bố mọi DB fingerprint bất biến. Orders/Payments giữ0.

## Current validation

| Check | Kết quả thực tế |
| --- | --- |
| ZIP / python-docx reopen | PASS |
| Images | 68 placements/67 distinct embedded media; cả68 figure hashes có trong DOCX, decode và relationships hợp lệ; Word deduplicate một ảnh giống byte |
| Source excerpts | 19 entries/anchors/transcripts xác minh; test paths chỉ kiểm tra hiện diện, narrative/CI references giữ provenance riêng |
| A4/TNR/heading/header/footer/PAGE/TOC | Có và render được; dọn theme font override/đường kẻ tiêu đề, sửa cột đầu; TOC hai cấp được Word update |
| Packaged renderer | BLOCKED do thiếu LibreOffice, không đổi failure thành PASS |
| Legitimate fallback renderer | PASS — Microsoft Word16.0 ExportAsFixedFormat + bundled Poppler,76 trang; DOCX vẫn editable |
| Visual QA | Xem76/76 PNG trang cuối ở native raster84DPI qua38 cặp trang; không thấy ảnh hỏng/text overlap/diagram bị cắt |
| Print/readability acceptance | HUMAN PENDING — một số historical full-page UI và JSON diagnostic nhỏ trên A4; xem file gốc full-resolution và HP chọn cách trình bày trước nộp |
| JSON/Markdown/manifest/Git diff | Validator chạy trước commit; kết quả cuối được ghi trong handoff/PR metadata |
| Full Java/Node/postgres-it local | NOT RUN trong pha tài liệu; CI publication được đọc riêng, không thay receipt cũ |

[Render receipt](follow-ups/20261101-validation/word-render-validation.json) giữ DOCX/PDF/hash76 PNG, phương pháp và giới hạn. [Structural record](structural-validation.json) và [manifest hiện tại](artifact-manifest.json) phân biệt lịch sử/mới. PDF/page PNG giữ ngoài Git cho QA, không thay deliverable editable DOCX.

## Remaining human gates

- **HUMAN PENDING**: HP/QĐ nội dung, chapter ownership, diagram/ảnh, print readability; PR76 chưa có approval, tiếp tục Draft.
- **NOT PROVIDED**: mẫu bìa chính thức/trường/lớp/học phần/giảng viên; không bịa.
- **NOT VERIFIED**: toàn bộ prompt/model history của nhóm chưa đủ;8 prompt gốc giữ nguyên byte.
- **NOT RUN**: clean-host/full demo/public SMTP rehearsal và video có mặt thành viên. Mailpit capture không đóng gate này.
- SCA, SMTP/timing, TLS/proxy/trusted IP/cookie/ACL, distributed/restart/load, backup/shared seed/QA/release giữ owner/gate riêng; không đổi QA01 overall,QA02/03,REL01/02.

**READY FOR HUMAN REVIEW**, chưa phải bản nộp đã nghiệm thu. Exact-head CI và working-tree clean được kiểm tra sau push; không lấy CI cũ làm kết quả HEAD mới.

---

# Historical validation snapshot 29/10/2026 retained verbatim below

Ngày đối chiếu: **29/10/2026**. Trạng thái: **PARTIAL — MISSING EVIDENCE**.
Đây là hồ sơ biên soạn và kiểm tra tài liệu, không phải nghiệm thu release hoặc một lần chạy lại application regression.

## Nguồn và phạm vi

- Master Plan gốc: `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`.
- SHA-256 Master Plan: `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
- Source/base develop: `6a26370659a2fa7b95c6a1d00d2598820de6bb34`.
- Branch: `docs/doc-01-hp-technical-documentation`.
- Tên chương giữ đúng phần O Master Plan: 1 Đề tài và phạm vi; 2 Kiến trúc và cấu trúc project; 4 Identity và Security; 5 Shop và Catalog; 12 Quản lý nhóm/Git.
- Account/Address được giải thích cùng phạm vi HP; kiểm thử/setup/demo ở phụ lục để TD/QĐ ghép chương10–11, không đổi ownership hoặc tự đặt lại tên chương12.
- Không tìm thấy mẫu bìa/hướng dẫn chi tiết của giảng viên hay bộ DOC-01/DOC-02 đã merge ngoài Master Plan và các quy ước repo. PR73 DOC-03 còn DRAFT khi đối chiếu; không lấy nội dung chưa merge làm chuẩn bắt buộc. HP bổ sung trường/lớp/học phần/giảng viên theo mẫu thật trước nộp.

## Sản phẩm đã tạo

| Thành phần | Kết quả thực tế |
|---|---|
| DOCX chỉnh sửa được | `HP_Chapters_1_2_4_5_12.docx`, 5 chương và4 phụ lục; 13.158 đơn vị tách bằng whitespace, không phải số trang đã render |
| Chức năng | 19 mục, activity/source/contracts/tests và điều kiện thành công/thất bại |
| Sơ đồ kiến trúc | 6, gồm5 góc nhìn yêu cầu và1 sơ đồ quan hệ persistence HP |
| Activity | 19, thể hiện đường xử lý chính; nhánh từ chối và transaction được giải thích trong văn bản |
| Sequence | 5: login, JWT, email verification, password reset, checkout boundary |
| Hình source | 19 trích dẫn thật, có số dòng, file và SHA-256; có transcript `.txt` |
| Ảnh UI thật | 5 ảnh kế thừa UI-02, giữ nguyên byte và provenance; không phải chụp mới hoặc CRUD verification trên DOC-01 |
| Hình nhúng DOCX | 54/54 relationships nội bộ hợp lệ, 54 captions và tham chiếu trong đoạn văn |
| Prompt | 8 file nguyên văn có thể truy xuất: 7 yêu cầu lịch sử HP cung cấp,1 DOC-01 hiện tại; không tự suy đoán model lịch sử |
| Hướng dẫn độc lập | Setup, demo storyboard, source index, prompt register |

## Kiểm tra đã thực thi

1. DOCX ZIP integrity và mở lại bằng `python-docx`: **PASS**. Đây là kiểm tra cấu trúc, không phải mở bằng Microsoft Word.
2. A4, Times New Roman, Heading1/2/3, header/footer/PAGE, TOC field, captions, alt text ảnh: **có trong OOXML**. Phải update TOC trong Word sau ghép, chưa xác minh phân trang.
3. 54 ảnh nhúng tồn tại; hashes của toàn bộ file hình khớp `figures.json`: **PASS**.
4. 19 excerpt hashes và nội dung source sau chuẩn hóa CRLF/LF khớp Git blob tại source SHA: **PASS**. Các controller/service path và tên Java test class nêu trong19 mục đều tồn tại; xem `structural-validation.json`. Không suy ra mọi test đều đã thực thi trong DOC-01.
5. 5 screenshot copies khớp SHA-256 của original index: **PASS**. `screenshot-provenance.json` ghi route/role/viewport/JAR/source của packet cũ và các UI path thay đổi đến source mới; ảnh chỉ là minh họa lịch sử có giới hạn.
6. 8 prompt copies khớp file gốc: **PASS**; full prompt ở `../prompts/`, không tạo lại lịch sử chat.
7. Đã xem contact sheets của6 architecture,19 activity,5 sequence,19 source và5 UI; đã sửa mũi tên quan hệ ERD và nhãn sequence bị cắt. Đây là kiểm tra **file hình**, không phải visual inspection từng trang Word.
8. Packaged renderer `render_docx.py … --output_dir .work/doc01/render --emit_pdf`: **BLOCKED**, `FileNotFoundError: LibreOffice soffice.exe was not found on PATH`. Retry dùng TEMP/TMP trong workspace loại lỗi sandbox temporary cleanup, vẫn thiếu renderer. Không có PDF/page PNG hoặc page count; không ghi visual layout PASS.
9. `git diff --check`: kiểm tra sau generation/staging, kết quả lưu trong receipt trước commit. Chỉ stage `docs/report/hp/`; không thay production/test/migration/security/CI.

`artifact-manifest.json` liệt kê hashes của sản phẩm cuối (trừ chính manifest để tránh hash tự tham chiếu). Kiểm tra lại sau mọi thay đổi DOCX/hình/tài liệu; không đổi hash để che artifact sai.

## GitHub và evidence lịch sử

`github-snapshot.json` lưu metadata đã đối chiếu qua GitHub connector:

- PR66 merged: reviewed HEAD `026a29deb6695e23380d0a95c5da68564add004e`; merge `b538dbc3af17ff80802fa33eea187705c40059fe`. QA-01 tested source `6e93d44614b1574ab2d310bae9a54ae57c385893`:694 unit/architecture +1346 PostgreSQL =2040 Java. QĐ33 Node +17 tooling; **không nói QĐ chạy lại PostgreSQL**.
- PR72 QA-01 scoped close-out và PR74 UI-02 đã merged. UI-02 CI171:705 Java +40 Node;240 render/containment không phải240 CRUD journeys. Native confirmation là HP manual evidence được chấp nhận giới hạn.
- PR70 merged tại source `6a26370`, HEAD `0f54c66c98f2441fa506e0fc1cf6e51fb27fb169`; hai commit có cùng Git tree. Standard CI175/run40835952619 và tooling run40835952644 SUCCESS. Full PostgreSQL run[40835952516](https://github.com/hphuc032/Logistic-UTEExpress/actions/runs/40835952516), job123320655073 SUCCESS; log xác nhận checkout exact0f54c66, chạy full-maven/targeted-refund và PR70 PASS. DOC-01 không giải mã archive hoặc tự gán test count của321ccf3 cho0f54c66.
- PR70 count2124 Java,33 Node,17 tooling thuộc receipt trước sync tại321ccf335; targeted709+205 overlap, không cộng vào full. Standard/new gate giữ riêng provenance.
- PR69 shared seed và PR73 DOC-03 vẫn OPEN/DRAFT tại thời điểm snapshot; không coi chúng đã merge hoặc seed/QA-03 PASS.

DOC-01 không chạy Java/Node/PostgreSQL suite; thay đổi chỉ là tài liệu. CI của PR tài liệu nếu chạy là nguồn evidence mới riêng, không thay các receipt trên.

## Những phần còn thiếu

- **14 mục MISSING SCREENSHOT**: xem `missing-screenshots.json` và ngay trong DOCX. HP bổ sung ảnh thật của đúng flow trên build xác định; với thao tác không có màn hình riêng như JWT/logout, dùng ảnh kết quả UI an toàn, không chụp token/cookie/OTP thật. Không có bằng chứng thì giữ nhãn thiếu.
- **Visual DOCX/pagination NOT VERIFIED**: cần môi trường renderer hoặc Word để kiểm tra toàn bộ trang, captions, source legibility, bảng, mục lục và ngắt trang. Kiểm tra ZIP không thay bước này.
- **Setup/demo NOT RUN**: cần QĐ cung cấp fixture/credential qua kênh riêng và database seed đã nghiệm thu; SMTP/CDN/storage, role và clean-host rehearsal chưa được DOC-01 chạy. Không có mật khẩu demo chuẩn được công bố.
- **Prompt history chưa đầy đủ**: chưa có toàn bộ follow-up/model logs của HP hoặc DOC-02/03 và prompt TD/QĐ; register ghi rõ cần bổ sung.
- **Source/document findings**: chưa tìm thấy Vendor shop editing/logo/banner flow; sort highestRated chưa có trong ProductSort. Mô tả source hiện tại, không sửa backend để khớp Plan hoặc tài liệu cũ.
- **Gate QA/release giữ nguyên**: QA-01 overall chưa COMPLETED; QA-02 IN PROGRESS; QA-03 BLOCKED. SCA, SMTP/timing, deployed TLS/proxy/trusted IP/cookie/ACL, distributed/restart/load, off-host backup/seed/manual/release cần owner riêng. DOC-01 không thêm risk acceptance.

## Checklist HP / QĐ trước bản nộp

- [ ] Review nội dung19 chức năng, đường dẫn và tên chương với Master Plan/mẫu giảng viên thật.
- [ ] Bổ sung14 ảnh cần thiết hoặc quyết định trình bày giới hạn được giảng viên chấp nhận; giữ provenance.
- [ ] Mở/render toàn bộ DOCX, cập nhật TOC/số trang và kiểm tra ảnh rõ khi in A4.
- [ ] Điền thông tin bìa học phần; ghép đúng chương TD/QĐ, không tạo chương trùng.
- [ ] Xác minh setup/seed và quay video có mặt HP theo storyboard; không ghi secret.
- [ ] Bổ sung prompt history có thể truy xuất; không suy đoán tác giả/model.
- [ ] Kiểm tra exact-head CI của PR tài liệu và final artifact manifest.

**Kết luận: PARTIAL — MISSING EVIDENCE.** Nội dung chương và sản phẩm Word đã có để HP review; chưa tuyên bố bản nộp cuối hoặc DOC-01/QA/release COMPLETED.
