# Làm việc nhóm

Hoàng Hiện chủ đạo backend, Minh Anh chủ đạo frontend. Có thể đổi vai trò theo task; thống nhất API trước để tránh chặn nhau.

1. Lấy `main` mới nhất và tạo nhánh `feat/<ten-chuc-nang>`, `fix/<loi>` hoặc `docs/<noi-dung>`.
2. Thay đổi nhỏ, có mục tiêu rõ; không sửa đồng thời nhiều module không liên quan.
3. Commit mô tả ý nghĩa: `feat(catalog): add product listing API`.
4. Chạy kiểm tra liên quan; mở PR, nêu ảnh hưởng migration và cách thử.
5. Người còn lại review, CI pass rồi merge. Quy trình này là thỏa thuận nhóm; branch protection chưa được cấu hình tự động.

## Quy ước

- Java package theo module nghiệp vụ; không trả JPA entity trực tiếp qua API.
- TypeScript strict; API client tập trung, dùng relative URL và cookie Session.
- Tiền dùng `BigDecimal`/PostgreSQL `numeric`, không dùng float/double cho nghiệp vụ thanh toán.
- Backend kiểm tra quyền, dữ liệu và ràng buộc; không chỉ dựa vào UI.
- Migration đã dùng chung không sửa; thêm file version mới.
- Không push `.env`, API keys, mật khẩu, email thật, database dump hoặc dữ liệu tracking thật.
- AI/automation không được tự gửi email thật hoặc tạo đơn thật trong test.

## Điều kiện hoàn thành một task

Chạy được local, có validation cần thiết, đúng phân quyền, test phù hợp, cập nhật tài liệu nếu đổi cách chạy/API. Các tính năng chưa hoàn thành phải được ghi rõ, không dùng số liệu demo như dữ liệu thật.

