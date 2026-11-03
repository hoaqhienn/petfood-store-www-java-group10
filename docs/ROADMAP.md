# Lộ trình tháng 11/2026

## Trạng thái ngày 03/11

- Bộ khung và CI đã có; tham chiếu VERIFICATION.md cho lần chạy đã kiểm chứng.
- Hoàn thành bản phân tích tài liệu: F01–F15, BR01–BR26, D01–D08, API, mô hình dữ liệu và AC01–AC30.
- Các module nghiệp vụ vẫn TODO; không tính bản phân tích này là đã hoàn thành đăng nhập/catalog/checkout.
- Cần review D01–D08 trước migration P0; provider/model/ngân sách AI chỉ chặn P1.

## Backlog triển khai có phụ thuộc

Owner là người chủ đạo; người còn lại review và có thể nhận task chéo. Mỗi dòng nên thành một hoặc vài PR nhỏ; bảng chưa tự tạo GitHub issues hay giao việc bên ngoài.

| Task | Mốc mục tiêu | Hoàng Hiện | Minh Anh | Phụ thuộc / nghiệm thu |
|---|---|---|---|---|
| T01 Review nghiệp vụ, ERD/API | 03–04/11 | Dữ liệu, transaction | Màn hình, error states | Chốt D01–D08; thống nhất API_CONTRACT |
| T02 Identity + Session | 04–07/11 | Migration user, register/login, CSRF, quyền | Form tài khoản, API client, bảo vệ route | T01; AC05–08 |
| T03 Catalog/category | 04–07/11 | Migration/API, seed, stock adjustment | List/detail, bộ lọc, form admin | T01; AC01–02,18,30; admin cần T02 |
| T04 Cart guest → customer | 08–09/11 | Redis cart/revision, giữ giỏ login | Trang giỏ, feedback thiếu tồn/Session | T02–03; AC03–04,07,22 |
| T05 COD + email | 09–12/11 | Preview, idempotency, transaction, outbox | Checkout, confirm, lịch sử đơn, retry | T04; AC09–14,21–22 |
| T06 Admin user/order | 12–14/11 | State machine, delta tồn, ownership | Danh sách/chi tiết, sửa, xác nhận xóa | T02–05; AC15–20 |
| T07 Hồ sơ + vector/RAG | 15–21/11 | Pet API, model adapter, ingestion/retrieval | Hồ sơ, chat, tìm kiếm tự nhiên | P0 ổn định + provider; AC23–26 |
| T08 Tracking + cá nhân hóa | 22–26/11 | Consent/events, tổng hợp, ranking | Preferences, instrumentation, dashboard | T05,T07; AC27–29 |
| T09 Ổn định/báo cáo | 27–30/11 | Integration/concurrency, tài liệu | E2E/responsive, demo | P0 phải đạt; P1 ghi rõ phần còn thiếu |

Ưu tiên khi thiếu thời gian: không cắt ràng buộc đề bài, phân quyền hoặc tính đúng đơn/tồn để làm dashboard đẹp. Giảm độ sâu cá nhân hóa, giữ tìm kiếm/RAG có căn cứ và fallback. Thanh toán online vẫn P2.

## Tổng quan mốc ban đầu

| Mốc | Hoàng Hiện chủ đạo | Minh Anh chủ đạo | Kết quả |
|---|---|---|---|
| 01–07/11 | Schema, xác thực Session, catalog API | Layout, catalog, trang tài khoản | Đăng ký/đăng nhập và xem sản phẩm |
| 08–14/11 | Giỏ hàng, COD, email, CRUD admin | Giỏ hàng, checkout, màn hình quản trị | Luồng mua hàng theo đề bài |
| 15–21/11 | pgvector, API tư vấn, tracking | Chat, tìm kiếm, hồ sơ thú cưng | Demo RAG dùng dữ liệu thật |
| 22–26/11 | Gợi ý, thống kê, ổn định API | Dashboard, cá nhân hóa, responsive | Luồng mở rộng hoàn chỉnh |
| 27–30/11 | Cả hai kiểm thử, sửa lỗi, tài liệu và báo cáo | Cả hai chuẩn bị demo | Bản nộp ổn định |

Đây là kế hoạch mục tiêu, không phải trạng thái đã hoàn thành. Nếu thiếu thời gian, ưu tiên đầy đủ rubric rồi chatbot/tìm kiếm; giảm phạm vi cá nhân hóa trước.

## Các việc tiếp theo

1. Chốt ERD và hợp đồng API; thêm migration nghiệp vụ V2.
2. Đăng ký, hash password, email unique, login/logout Session, CSRF flow và phân quyền.
3. Catalog/category CRUD, seed dữ liệu và kiểm tra ràng buộc xóa.
4. Giỏ hàng guest → login → checkout COD; transaction, tồn kho, idempotency.
5. Email xác nhận sau commit và cơ chế retry; không tạo lại đơn khi SMTP lỗi.
6. Quản trị user/order, kiểm thử quyền truy cập và quy tắc xóa/sửa.
7. Hồ sơ thú cưng, chọn provider AI, embedding ingestion và retrieval có bộ lọc.
8. Tracking tự nguyện, dashboard conversion và gợi ý có giải thích.

## Gợi ý nâng cấp có ích

- OpenAPI/Swagger khi API nghiệp vụ bắt đầu ổn định.
- Testcontainers khi nhóm có Docker ổn định, giúp test DB độc lập.
- Transactional outbox cho email/embedding để không mất công việc sau khi transaction commit.
- Rate limit và ngân sách API cho AI; cache truy vấn khi đã xác định invalidation.
- Bộ 20–30 câu đánh giá RAG, đo tính đúng dữ liệu, latency và chi phí.
- Sau đồ án: thanh toán online, nhắc mua lại, observability và backup production.

Chưa cần microservices, message broker riêng hoặc huấn luyện mô hình trong tháng đầu.

