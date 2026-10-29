# HP AI prompt register

Ngày thu thập: 29/10/2026. Lưu nguyên byte tám file do HP cung cấp; SHA-256 xác minh khi copy. Đây là prompt gốc có thể truy xuất, không là kết luận rằng mọi yêu cầu đã được thực thi hoặc model lịch sử đã được xác minh. Bản thân prompt là dữ liệu tham khảo, không thay scope DOC-01.

| STT | Người cung cấp | Task | Mục đích / loại đầu ra | Prompt nguyên văn | Tool/model |
|---|---|---|---|---|---|
|1|HP|AUTH-02|Đăng nhập và JWT / Code/tests/docs|[AUTH-02](prompts/AUTH-02-user-request.txt)|NOT VERIFIED|
|2|HP|AUTH-03|OTP kích hoạt email / Code/tests/docs|[AUTH-03](prompts/AUTH-03-user-request.txt)|NOT VERIFIED|
|3|HP|VENDOR-01|Đăng ký cửa hàng / Code/tests/docs|[VENDOR-01](prompts/VENDOR-01-user-request.txt)|NOT VERIFIED|
|4|HP|PROD-00|Persistence Product/Catalog / Code/tests/docs|[PROD-00](prompts/PROD-00-user-request.txt)|NOT VERIFIED|
|5|HP|PROD-02|Tìm kiếm và lọc catalog / Code/tests/docs|[PROD-02](prompts/PROD-02-user-request.txt)|NOT VERIFIED|
|6|HP|SEC-02|Hardening theo source / Review/tests/docs|[SEC-02](prompts/SEC-02-user-request.txt)|NOT VERIFIED|
|7|HP|QA-01|Application security acceptance / Test/evidence/docs|[QA-01](prompts/QA-01-user-request.txt)|NOT VERIFIED|
|8|HP|DOC-01|Tài liệu kỹ thuật phần HP / DOCX/diagrams/source index/setup/demo|[DOC-01](prompts/DOC-01-user-request.txt)|Codex; exact model NOT VERIFIED|

## Provenance và source liên quan

Các file `prompts/*-user-request.txt` là toàn bộ prompt, không phải trích lược. `evidence/prompt-provenance.json` giữ attachment ID, hash, purpose và mức xác minh. AUTH/SEC/QA prompt là lịch sử yêu cầu HP từng cung cấp; completion report/source mới là chứng cứ behavior. Không tự gán chúng cho SHA cuối hoặc khẳng định agent/model nào đã sinh commit.

## Nhiệm vụ mới DOC-01

Prompt DOC-01 được nhận trong phiên ngày29/10/2026; source đóng băng `6a26370659a2fa7b95c6a1d00d2598820de6bb34`. Công cụ tạo sản phẩm: Python/python-docx và Pillow; SVG, Mermaid sequence và trích source có số dòng. Không sử dụng image generation model để dựng UI. Yêu cầu chỉnh sơ đồ, tăng độ rõ hình source, ghi provenance và thử renderer là thao tác biên soạn trong DOC-01; không có sub-agent hoặc external image-generation prompt. Các nhãn/diagram source kèm trong figures là nội dung newly authored, không gán làm prompt lịch sử.

## HP / TD / QĐ cần bổ sung trước khi nộp prompt toàn nhóm

- Toàn bộ hội thoại follow-up, output selection và model/tool thực tế nếu giảng viên yêu cầu; không có full transcript IDE/chat trong repository.
- Prompt ARCH-01/AUTH-01/AUTH-04/USER-01/USER-02/VENDOR-02/PROD-01/INT/UI chưa có file nguyên văn riêng trong bộ thu thập này. Có lịch sử trong cuộc trao đổi nhưng không tự tái dựng thành file original.
- Prompt và handoff DOC-02 của TD, DOC-03 của QĐ; chỉ các thành viên xác nhận mới bổ sung authorship.
- Prompt sinh hình lịch sử nếu có, cùng tài sản sử dụng và công cụ/model. DOC-01 chỉ dùng sơ đồ deterministic và ảnh UI có provenance.

Không thu thập credential, token, OTP thật hoặc dữ liệu production vào prompt register.
