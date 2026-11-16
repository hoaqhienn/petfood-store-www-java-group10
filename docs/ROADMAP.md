# Lộ trình tháng 11/2026

## Trạng thái ngày 16/11

- Đã merge PR #1–#28: phân tích/UML (#1–#8), catalog và giỏ (#9–#11), tài khoản (#12), checkout COD (#13–#16), quản trị đơn/người dùng/catalog/email (#17, #19–#23), test cạnh tranh và giỏ nhiều tab (#24–#25), hồ sơ thú cưng (#26), AI Gemini (#27), tracking (#28).
- **Backend P0 và P1 đã xong**, kể cả T07–T08 sớm hơn kế hoạch. Trạng thái từng AC ở [ACCEPTANCE.md](ACCEPTANCE.md#trạng-thái-kiểm-chứng-ngày-1611).
- **Rủi ro lớn nhất là giao diện:** frontend mới có catalog và giỏ hàng; còn tài khoản, checkout, đơn hàng, quản trị, chat AI, banner đồng ý và dashboard.
- Chưa chạy thử Gemini với API key thật; máy của Hoàng Hiện chưa chạy được Docker nên chưa demo local trọn luồng.

## Backlog triển khai có phụ thuộc

Rà soát sau bản phân tích: dùng thêm CONSTRAINTS.md và AC31–AC36. T02 cần bootstrap admin/DTO whitelist; T04 cần kiểm thử giỏ nhiều tab; T05 cần quyền replay và cạnh tranh ẩn SKU/category. Tổng số kịch bản hiện là 36.

Owner là người chủ đạo; người còn lại review và có thể nhận task chéo. Mỗi dòng nên thành một hoặc vài PR nhỏ; bảng chưa tự tạo GitHub issues hay giao việc bên ngoài.

| Task | Mốc mục tiêu | Hoàng Hiện | Anh Minh | Phụ thuộc / nghiệm thu | Trạng thái 16/11 |
|---|---|---|---|---|---|
| T01 Review nghiệp vụ, ERD/API | 03–04/11 | Dữ liệu, transaction | Màn hình, error states | Chốt D01–D08; thống nhất API_CONTRACT | Xong (PR #1–#8) |
| T02 Identity + Session | 04–07/11 | Migration user, register/login, CSRF, quyền | Form tài khoản, API client, bảo vệ route | T01; AC05–08 | Backend xong (#12, #19, #21); thiếu form tài khoản |
| T03 Catalog/category | 04–07/11 | Migration/API, seed, stock adjustment | List/detail, bộ lọc, form admin | T01; AC01–02,18,30; admin cần T02 | Backend xong (#9, #22); giao diện list/detail xong (#11); thiếu form admin |
| T04 Cart guest → customer | 08–09/11 | Redis cart/revision, giữ giỏ login | Trang giỏ, feedback thiếu tồn/Session | T02–03; AC03–04,07,22 | Xong giỏ guest + giữ giỏ khi login (#10–#12), có test nhiều tab (AC31) |
| T05 COD + email | 09–12/11 | Preview, idempotency, transaction, outbox | Checkout, confirm, lịch sử đơn, retry | T04; AC09–14,21–22 | Backend xong (#13–#16, #23, #24); thiếu giao diện checkout/lịch sử |
| T06 Admin user/order | 12–14/11 | State machine, delta tồn, ownership | Danh sách/chi tiết, sửa, xác nhận xóa | T02–05; AC15–20 | Backend xong (#17, #20, #21); thiếu giao diện quản trị |
| T07 Hồ sơ + vector/RAG | 15–21/11 | Pet API, model adapter, ingestion/retrieval | Hồ sơ, chat, tìm kiếm tự nhiên | P0 ổn định + provider; AC23–26 | Backend xong (#26, #27, Gemini); thiếu giao diện, chưa thử key thật |
| T08 Tracking + cá nhân hóa | 22–26/11 | Consent/events, tổng hợp, ranking | Preferences, instrumentation, dashboard | T05,T07; AC27–29 | Backend xong (#28); thiếu giao diện |
| T09 Ổn định/báo cáo | 27–30/11 | Integration/concurrency, tài liệu | E2E/responsive, demo | P0 phải đạt; P1 ghi rõ phần còn thiếu | Đã có dữ liệu demo và bảng trạng thái AC; còn test các AC thiếu, E2E, kịch bản demo, báo cáo |

Ưu tiên khi thiếu thời gian: không cắt ràng buộc đề bài, phân quyền hoặc tính đúng đơn/tồn để làm dashboard đẹp. Giảm độ sâu cá nhân hóa, giữ tìm kiếm/RAG có căn cứ và fallback. Thanh toán online vẫn P2.

## Tổng quan mốc ban đầu

| Mốc | Hoàng Hiện chủ đạo | Anh Minh chủ đạo | Kết quả |
|---|---|---|---|
| 01–07/11 | Schema, xác thực Session, catalog API | Layout, catalog, trang tài khoản | Đăng ký/đăng nhập và xem sản phẩm |
| 08–14/11 | Giỏ hàng, COD, email, CRUD admin | Giỏ hàng, checkout, màn hình quản trị | Luồng mua hàng theo đề bài |
| 15–21/11 | pgvector, API tư vấn, tracking | Chat, tìm kiếm, hồ sơ thú cưng | Demo RAG dùng dữ liệu thật |
| 22–26/11 | Gợi ý, thống kê, ổn định API | Dashboard, cá nhân hóa, responsive | Luồng mở rộng hoàn chỉnh |
| 27–30/11 | Cả hai kiểm thử, sửa lỗi, tài liệu và báo cáo | Cả hai chuẩn bị demo | Bản nộp ổn định |

Đây là kế hoạch mục tiêu, không phải trạng thái đã hoàn thành. Nếu thiếu thời gian, ưu tiên đầy đủ rubric rồi chatbot/tìm kiếm; giảm phạm vi cá nhân hóa trước.

## Các việc tiếp theo

1. Giao diện đăng nhập/đăng ký, checkout COD, lịch sử và hủy đơn.
2. Giao diện quản trị: đơn, người dùng, danh mục/sản phẩm/tồn kho, email lỗi.
3. Sửa Docker trên máy Hoàng Hiện, chạy thử trọn luồng với dữ liệu demo và Gemini key thật.
4. Bổ sung test cho các AC còn thiếu (xem ACCEPTANCE.md) và E2E giao diện.
5. Giao diện hồ sơ thú cưng, chat AI, banner đồng ý tracking, dashboard.
6. Kịch bản demo và báo cáo.

Nếu thiếu thời gian, ưu tiên 1–4 (P0 theo đề bài) trước phần giao diện P1.

## Gợi ý nâng cấp có ích

- OpenAPI/Swagger khi API nghiệp vụ bắt đầu ổn định.
- Testcontainers khi nhóm có Docker ổn định, giúp test DB độc lập.
- Transactional outbox cho email/embedding để không mất công việc sau khi transaction commit.
- Rate limit và ngân sách API cho AI; cache truy vấn khi đã xác định invalidation.
- Bộ 20–30 câu đánh giá RAG, đo tính đúng dữ liệu, latency và chi phí.
- Sau đồ án: thanh toán online, nhắc mua lại, observability và backup production.

Chưa cần microservices, message broker riêng hoặc huấn luyện mô hình trong tháng đầu.

