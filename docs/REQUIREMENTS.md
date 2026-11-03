# Chức năng và phạm vi — cập nhật 03/11/2026

Đây là đặc tả để triển khai, **không phải danh sách chức năng đã chạy**. Repo hiện vẫn là bộ khung API giới thiệu/health, trang giới thiệu, hạ tầng và CI. Bản này giữ yêu cầu môn học và bổ sung đề xuất nghiệp vụ để review.

## Danh mục chức năng

P0 = yêu cầu môn học và luồng mua hàng đúng; P1 = mở rộng người dùng đã yêu cầu, làm sau P0; P2 = sau bản nộp.

| ID | Chức năng / kết quả | Mức | Nguồn |
|---|---|---|---|
| F01 | Danh sách/chi tiết từ CSDL; tìm tên, phân trang, lọc loài/loại/giá | P0 | Đề bài + hỗ trợ sử dụng |
| F02 | Guest thêm/xem/sửa số lượng/xóa giỏ Session, không có bảng giỏ | P0 | Đề bài |
| F03 | Đăng ký email duy nhất, hash mật khẩu, lưu CSDL, email và thông báo | P0 | Đề bài |
| F04 | Login/logout Session, giữ giỏ khi login, kiểm tra vai trò | P0 | Điều kiện checkout |
| F05 | Checkout COD, lưu đơn/chi tiết, email, xóa giỏ sau thành công | P0 | Đề bài + chọn COD |
| F06 | Đơn của tôi: lịch sử/chi tiết, trạng thái, hủy khi chờ xác nhận | P0 | Đề xuất hỗ trợ tra cứu/hủy |
| F07 | Admin tìm/xem/thêm/sửa/xóa sản phẩm và loại theo ràng buộc | P0 | Đề bài |
| F08 | Admin tìm/xem/cập nhật/xóa user đủ điều kiện; không lộ password/hash | P0 | Đề bài |
| F09 | Admin tìm/xem đơn theo ngày, sửa số lượng, xử lý trạng thái | P0 | Đề bài + vận hành |
| F10 | Email đăng ký/đơn hàng, Mailpit hoặc SMTP thật, retry lỗi | P0 | Đề bài + lựa chọn dự án |
| F11 | Nhiều hồ sơ thú cưng/user, không bắt buộc để mua | P1 | Người dùng xác nhận |
| F12 | Tìm kiếm tự nhiên theo ngữ nghĩa kết hợp bộ lọc và CSDL | P1 | Người dùng yêu cầu AI/vector |
| F13 | Chatbot RAG theo hồ sơ, gợi ý có lý do và link sản phẩm | P1 | Định hướng dự án |
| F14 | Tracking theo lựa chọn khách, dashboard funnel và tương tác gợi ý | P1 | Người dùng bổ sung |
| F15 | Cá nhân hóa theo hồ sơ/nội dung/hành vi được phép, có fallback | P1 | Người dùng bổ sung |

F01–F15 đều **chưa triển khai nghiệp vụ**. UI placeholder và extension vector chưa chứng minh hoàn thành chức năng. P2: online payment, vận chuyển tích hợp, đánh giá, voucher, nhắc mua lại.

## Ma trận quyền

| Hành động | Guest | Customer | Admin |
|---|---|---|---|
| Catalog công khai / giỏ Session | Có | Có | Có |
| Đăng ký / đăng nhập | Có | Không cần đăng ký lại | Không cần đăng ký lại |
| Checkout COD | Phải login | Có | Có |
| Hồ sơ, thú cưng, đơn mua | Không | Chỉ của mình | Chỉ của mình qua API khách |
| Quản lý sản phẩm/loại | Không | Không | Có qua API admin |
| Tìm/xem/sửa user | Không | Không | Theo trường cho phép |
| Tìm/xem/sửa mọi đơn | Không | Không | Theo trạng thái |
| AI tìm kiếm/chat | Có, quota thấp | Có | Có |
| Cá nhân hóa hồ sơ | Không | Có khi bật | Có khi bật |
| Dashboard tổng hợp | Không | Không | Có |

Guest không có bản ghi user trong DB. Đăng ký luôn tạo CUSTOMER; client không tự chọn ADMIN. Ẩn nút UI không thay thế authorization/ownership backend.

## Màn hình và trạng thái cần thiết

| Khu vực | Màn hình | Trạng thái phải thiết kế |
|---|---|---|
| Cửa hàng | Trang chủ, danh sách, chi tiết, giỏ | Loading, rỗng, hết hàng, sản phẩm ẩn, lỗi API |
| Tài khoản | Đăng ký, login, hồ sơ | Email trùng, lỗi từng trường, login sai, tài khoản khóa |
| Mua hàng | Checkout, xác nhận, lịch sử, chi tiết | Hết Session, giá thay đổi, thiếu tồn, đang gửi, retry |
| Admin | Sản phẩm/loại, user, đơn | Tìm/phân trang, cấm xóa, xung đột cập nhật, thiếu quyền |
| P1 | Thú cưng, chat, lựa chọn tracking | Thiếu hồ sơ, AI timeout, không có kết quả, từ chối tracking |

## Bộ tài liệu triển khai

- [Quy tắc nghiệp vụ và quyết định đề xuất](BUSINESS_RULES.md)
- [Ma trận ràng buộc, nơi thực thi và coverage](CONSTRAINTS.md)
- [Mô hình dữ liệu dự kiến](DOMAIN_MODEL.md)
- [Hợp đồng API dự thảo](API_CONTRACT.md)
- [Kịch bản nghiệm thu](ACCEPTANCE.md)
- [Mốc triển khai và phân công](ROADMAP.md)

P0 hoàn thành khi các kịch bản P0 đạt, hai thành viên chạy được theo README, có dữ liệu demo rõ nguồn và không có secret trong repo. P1 báo cáo theo phần chạy thật.

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
- Quy tắc tồn/hủy/sửa đơn, phí ship demo, xác minh email và tracking có phương án **đề xuất D01–D08** trong BUSINESS_RULES.md; cần review trước code, chưa coi là được người dùng phê duyệt.

Các điểm này không chặn việc chạy bộ khung. Chốt trước khi triển khai module liên quan.

