# Phạm vi đã thống nhất

## Yêu cầu môn học

- Guest: xem danh sách/chi tiết từ CSDL, chọn sản phẩm, xem và sửa số lượng giỏ Session; đăng ký email không trùng, lưu CSDL và gửi email/thông báo.
- Customer: có chức năng guest; đăng nhập trước checkout, lưu đơn/chi tiết đơn, gửi email/thông báo thành công và xóa giỏ trong Session.
- Admin: có chức năng customer; tìm kiếm sản phẩm/loại sản phẩm, tài khoản, đơn hàng.
- Admin quản lý danh sách/chi tiết/thêm/sửa sản phẩm và loại sản phẩm. Chỉ xóa sản phẩm chưa thuộc đơn nào; chỉ xóa loại chưa có sản phẩm.
- Admin xem danh sách/chi tiết/cập nhật tài khoản nhưng không thấy password; chỉ xóa tài khoản chưa đặt hàng.
- Admin xem đơn theo ngày mua, xem chi tiết và sửa số lượng mặt hàng. Phải tính lại tổng, kiểm tra tồn kho và xác định trạng thái được phép sửa trước khi triển khai.
- Validation phía server; frontend hỗ trợ trải nghiệm. Cấm tự viết database function, CHECK constraints, stored procedures. Được dùng pgvector và hàm/toán tử của extension.

## Quyết định dự án

- Tên hiển thị PetFood; repo `petfood-store-www-java-group10` public.
- Website React tiếng Việt; không làm ứng dụng di động.
- Spring Boot, Gradle, PostgreSQL, Redis, Docker; local first.
- COD trước, online payment sau; không yêu cầu cổng thanh toán ở bộ khung.
- Email Mailpit cho local, SMTP thật qua cấu hình.
- Một tài khoản có nhiều hồ sơ thú cưng; thông tin cơ bản và sở thích là tùy chọn.
- AI dùng API + RAG với pgvector; tự xây retrieval/prompt/ranking, chưa fine-tune mô hình.
- Tracking và cá nhân hóa là phần mở rộng sau các chức năng bắt buộc.

## Các quyết định còn mở

- Nhà cung cấp/model AI, embedding dimension và ngân sách API.
- Bộ dữ liệu sản phẩm thực tế, ảnh và quyền sử dụng nội dung.
- Quy tắc tồn kho, hủy đơn và trạng thái cho phép admin sửa số lượng.
- Xác minh email có bắt buộc trước mua hàng không (đề bài hiện yêu cầu gửi email thông báo).
- Thời hạn giữ dữ liệu tracking và cách xin lựa chọn cá nhân hóa.

Các điểm này không chặn việc chạy bộ khung. Chốt trước khi triển khai module liên quan.

