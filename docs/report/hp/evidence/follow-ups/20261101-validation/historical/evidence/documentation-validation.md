# DOC-01 — Kiểm tra tài liệu phần HP

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
