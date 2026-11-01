# Lộ trình tháng 11/2026

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

