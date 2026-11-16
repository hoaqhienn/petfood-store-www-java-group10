# Lộ trình tháng 11/2026

## Trạng thái ngày 16/11

- Đã merge PR #1–#17: tài liệu phân tích/UML (03–04/11), catalog (#9), giỏ hàng Session (#10), giao diện catalog/giỏ (#11), tài khoản Session (#12), checkout COD (#13–#14), lịch sử/hủy đơn (#15–#16), quản trị đơn chỉ đọc (#17).
- Backend đã đủ luồng mua COD từ giỏ guest đến email xác nhận. Frontend mới có catalog và giỏ hàng.
- **Trễ so với kế hoạch:** T02–T05 dự kiến xong 12/11 nhưng còn thiếu giao diện tài khoản/checkout/đơn hàng; T06 mới có phần đọc; T07 (15–21/11) chưa bắt đầu.
- **Nợ kiểm thử:** integration test mới phủ catalog, giỏ và hạ tầng. Tài khoản, đặt/hủy đơn, outbox email và quản trị đơn chưa có test.
- Migration chưa tạo tài khoản ADMIN; cần cơ chế bootstrap trước khi demo quản trị.

## Backlog triển khai có phụ thuộc

Rà soát sau bản phân tích: dùng thêm CONSTRAINTS.md và AC31–AC36. T02 cần bootstrap admin/DTO whitelist; T04 cần kiểm thử giỏ nhiều tab; T05 cần quyền replay và cạnh tranh ẩn SKU/category. Tổng số kịch bản hiện là 36.

Owner là người chủ đạo; người còn lại review và có thể nhận task chéo. Mỗi dòng nên thành một hoặc vài PR nhỏ; bảng chưa tự tạo GitHub issues hay giao việc bên ngoài.

| Task | Mốc mục tiêu | Hoàng Hiện | Anh Minh | Phụ thuộc / nghiệm thu | Trạng thái 16/11 |
|---|---|---|---|---|---|
| T01 Review nghiệp vụ, ERD/API | 03–04/11 | Dữ liệu, transaction | Màn hình, error states | Chốt D01–D08; thống nhất API_CONTRACT | Xong (PR #1–#8) |
| T02 Identity + Session | 04–07/11 | Migration user, register/login, CSRF, quyền | Form tài khoản, API client, bảo vệ route | T01; AC05–08 | Backend xong (#12); thiếu form tài khoản, bootstrap admin, test |
| T03 Catalog/category | 04–07/11 | Migration/API, seed, stock adjustment | List/detail, bộ lọc, form admin | T01; AC01–02,18,30; admin cần T02 | API đọc + giao diện list/detail xong (#9, #11); thiếu admin CRUD, điều chỉnh tồn |
| T04 Cart guest → customer | 08–09/11 | Redis cart/revision, giữ giỏ login | Trang giỏ, feedback thiếu tồn/Session | T02–03; AC03–04,07,22 | Xong giỏ guest + giữ giỏ khi login (#10–#12); chưa test nhiều tab |
| T05 COD + email | 09–12/11 | Preview, idempotency, transaction, outbox | Checkout, confirm, lịch sử đơn, retry | T04; AC09–14,21–22 | Backend xong (#13–#16); thiếu giao diện checkout/lịch sử, test |
| T06 Admin user/order | 12–14/11 | State machine, delta tồn, ownership | Danh sách/chi tiết, sửa, xác nhận xóa | T02–05; AC15–20 | Mới có tìm kiếm/xem đơn (#17); thiếu đổi trạng thái/số lượng, quản lý user |
| T07 Hồ sơ + vector/RAG | 15–21/11 | Pet API, model adapter, ingestion/retrieval | Hồ sơ, chat, tìm kiếm tự nhiên | P0 ổn định + provider; AC23–26 | Chưa bắt đầu |
| T08 Tracking + cá nhân hóa | 22–26/11 | Consent/events, tổng hợp, ranking | Preferences, instrumentation, dashboard | T05,T07; AC27–29 | Chưa bắt đầu |
| T09 Ổn định/báo cáo | 27–30/11 | Integration/concurrency, tài liệu | E2E/responsive, demo | P0 phải đạt; P1 ghi rõ phần còn thiếu | Chưa bắt đầu |

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

1. Integration test cho đăng ký/đăng nhập, đặt đơn COD (tồn kho, gửi lặp, cạnh tranh), hủy đơn và quản trị đơn.
2. Bootstrap tài khoản ADMIN cho môi trường local/demo.
3. Giao diện đăng nhập/đăng ký, checkout COD, lịch sử và hủy đơn.
4. T06: đổi trạng thái và số lượng đơn có delta tồn, quản lý user; giao diện quản trị đơn.
5. T03 còn lại: CRUD sản phẩm/danh mục và điều chỉnh tồn cho admin.
6. Hồ sơ thú cưng, chọn provider AI, embedding ingestion và retrieval có bộ lọc.
7. Tracking tự nguyện, dashboard conversion và gợi ý có giải thích.

Nếu thiếu thời gian, ưu tiên 1–5 (P0 theo đề bài) trước khi làm AI và tracking.

## Gợi ý nâng cấp có ích

- OpenAPI/Swagger khi API nghiệp vụ bắt đầu ổn định.
- Testcontainers khi nhóm có Docker ổn định, giúp test DB độc lập.
- Transactional outbox cho email/embedding để không mất công việc sau khi transaction commit.
- Rate limit và ngân sách API cho AI; cache truy vấn khi đã xác định invalidation.
- Bộ 20–30 câu đánh giá RAG, đo tính đúng dữ liệu, latency và chi phí.
- Sau đồ án: thanh toán online, nhắc mua lại, observability và backup production.

Chưa cần microservices, message broker riêng hoặc huấn luyện mô hình trong tháng đầu.

