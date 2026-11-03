# Kịch bản nghiệm thu dự kiến — 03/11/2026

Tất cả dòng dưới đây là **TODO**, chưa phải kết quả test. CI hiện tại chỉ kiểm chứng bộ khung theo [VERIFICATION.md](VERIFICATION.md). P0 phải pass trước khi coi nghiệp vụ hoàn thành; P1 theo scope mở rộng.

Chuẩn bị: guest, customer A/B, admin, user bị khóa; 2 loại, SKU ACTIVE/INACTIVE/hết hàng/còn 1 đơn vị; đơn ở các trạng thái. Email local dùng Mailpit. Dữ liệu demo ghi nhãn, không tạo email/đơn thật. Trường hợp thay đổi DB phải reset fixture độc lập.

| ID | Feature / rule | Mức | Kịch bản → kết quả mong đợi |
|---|---|---|---|
| AC01 | F01 / BR01–03 | P0 | Danh sách/chi tiết lấy từ DB; lọc/phân trang đúng; không kết quả có empty state; ẩn category làm SKU biến mất khỏi public |
| AC02 | F01–02 / BR03,10 | P0 | SKU hết hàng vẫn xem được nhưng thêm/checkout bị từ chối; SKU ẩn không lộ qua ID |
| AC03 | F02 / BR10 | P0 | Guest thêm/sửa/xóa và refresh giữ giỏ; không có bản ghi giỏ trong PostgreSQL; Session được lưu ở Redis |
| AC04 | F02 / BR10 | P0 | Quantity 0/âm/thập phân/>99 và quá 50 SKU bị từ chối; giỏ không đổi khi lỗi |
| AC05 | F03 / BR05–06 | P0 | Đăng ký hợp lệ tạo CUSTOMER, hash không bằng password; email/thông báo đúng, mail không chứa password |
| AC06 | F03 / BR05 | P0 | Hai request email tương đương sau trim/lowercase → chỉ một user, request còn lại EMAIL_EXISTS |
| AC07 | F04 / BR07 | P0 | Login đổi session ID, giữ giỏ; logout/hết phiên không truy cập API customer, không tự gửi lại mua hàng |
| AC08 | F04,08 / BR07–08 | P0 | Guest/customer gọi admin bị chặn; thiếu CSRF không sửa DB; tài khoản vừa khóa mất quyền trên Session cũ |
| AC09 | F05 / BR11–12 | P0 | Checkout giỏ rỗng/thiếu địa chỉ bị chặn; sửa giá ở client không làm thay giá server; giá đổi sau preview trả 409 |
| AC10 | F05 / BR13,15 | P0 | COD hợp lệ → một đơn + các dòng snapshot, PENDING/UNPAID, trừ tồn, outbox; giỏ đúng revision được xóa, login vẫn còn |
| AC11 | F05 / BR13 | P0 | Hai khách mua SKU còn 1 → chỉ một đơn thành công, tồn không âm; nhiều dòng có một dòng thiếu thì rollback toàn bộ |
| AC12 | F05 / BR14 | P0 | Submit lặp/cùng key kể cả sau giỏ xóa/quote hết hạn → cùng orderId, không trừ tồn/gửi event thêm |
| AC13 | F05 / BR14 | P0 | Cùng key khác payload → 409; Redis cleanup lỗi sau commit không tạo lại đơn; giỏ mới ở tab khác được giữ |
| AC14 | F06 / BR15 | P0 | B sửa ID để xem đơn A → 404, không PII; admin namespace riêng có quyền xem |
| AC15 | F06,09 / BR16–18 | P0 | Đúng state machine; khách chỉ hủy PENDING, admin hủy trước SHIPPING; hủy lặp/đồng thời sửa không hoàn tồn hai lần |
| AC16 | F09 / BR16–17 | P0 | Admin tăng/giảm quantity ở PENDING/CONFIRMED cập nhật chênh lệch tồn/tổng; version cũ hoặc thiếu tồn rollback |
| AC17 | F09 / BR15–18 | P0 | Không sửa SHIPPING/COMPLETED/CANCELLED; đổi giá/tên SKU không đổi dòng đơn; thiếu codReceived không hoàn thành COD |
| AC18 | F07 / BR04 | P0 | CRUD/tìm/list/detail sản phẩm/loại; xóa được khi chưa tham chiếu, không xóa SKU trong đơn hủy hoặc loại có SKU ẩn |
| AC19 | F08 / BR08–09 | P0 | Tìm/list/detail/update user không lộ hash; chỉ xóa user chưa đặt; không xóa/khóa admin hiện tại/cuối cùng |
| AC20 | F09 / BR18 | P0 | Tìm đơn theo mã/ngày/trạng thái, sort mới nhất và phân trang ổn định; doanh thu không cộng PENDING/CANCELLED |
| AC21 | F10 / BR19–20 | P0 | SMTP lỗi sau commit → user/đơn vẫn tồn tại, retry outbox; không nhân đôi nghiệp vụ; Mailpit thấy đúng mail khi phục hồi |
| AC22 | F02,05 / BR10,14 | P0 | Redis mất trước thao tác → lỗi rõ, không mất giỏ giả; mất sau commit → UI vẫn nhận order/retry cùng key an toàn |
| AC23 | F11 / BR21 | P1 | CRUD nhiều thú cưng, không bắt buộc để mua, owner isolation; xóa pet không ảnh hưởng đơn |
| AC24 | F12–13 / BR22–23 | P1 | Hỏi theo loài/tuổi/ngân sách → chỉ SKU hợp lệ, link thật, tối đa 3; không có kết quả không bịa/nới lọc |
| AC25 | F12–13 / BR23 | P1 | Đổi/ẩn/xóa SKU khi vector stale không trả sai giá/trạng thái; model khác dimension bị ngăn trộn |
| AC26 | F12–13 / BR22–23 | P1 | Nội dung sản phẩm chứa chỉ thị độc hại không làm AI gọi SQL/ghi đơn; timeout/quota fallback, mua thường vẫn chạy |
| AC27 | F14–15 / BR24–26 | P1 | Chưa consent không thu optional events; rút quyền ngừng ghi và dọn dữ liệu liên quan; order nghiệp vụ vẫn giữ |
| AC28 | F14 / BR25–26 | P1 | EventId lặp dedupe, client không giả order_placed; CTR/funnel đúng mẫu đã tính tay, ghi rõ tập consent/demo |
| AC29 | F15 / BR24 | P1 | Người mới/từ chối cá nhân hóa có fallback; không dùng hồ sơ/hành vi người khác để giải thích gợi ý |
| AC30 | F07 / BR02 | P0 | Điều chỉnh tồn có reason/version, không âm, có audit; request stale không ghi đè tồn của checkout |

## Cách phân bổ kiểm thử

### Bổ sung từ rà soát ràng buộc

Các dòng này cũng là TODO, chưa chạy; xem [CONSTRAINTS.md](CONSTRAINTS.md).

| ID | Feature / rule | Mức | Kịch bản → kết quả mong đợi |
|---|---|---|---|
| AC31 | F02,05 / BR10,14 | P0 | Hai tab sửa cùng revision → chỉ một ghi thành công, còn lại 409; cleanup checkout đồng thời không xóa revision mới |
| AC32 | F05 / BR14–15 | P0 | A đặt rồi logout, B/guest dùng lại key/body → không nhận đơn A; user A bị khóa không replay được đơn qua Session cũ |
| AC33 | F03,07–08 / BR06,08 | P0 | Body register/PATCH nhét role/status/stock ngoài quyền → bị từ chối, không cấp admin hay thay đổi trường được bảo vệ |
| AC34 | F04,08 / BR08 | P0 | Bootstrap admin chạy hai lần không tạo trùng/reset password; không có credential cố định trong source/log |
| AC35 | F01,05,07 / BR02,12 | P0 | SKU trùng, giá/trọng lượng không dương, số vượt miền biểu diễn, dữ liệu nhận hàng thiếu/sai → lỗi trường rõ, không ghi một phần; giới hạn cụ thể cần chốt trước DTO |
| AC36 | F05,07 / BR03,12–13 | P0 | Checkout cạnh tranh ẩn loại/SKU: nếu ẩn hoàn tất trước thì checkout thất bại; nếu checkout hoàn tất trước thì đơn snapshot hợp lệ và sau đó SKU bị ẩn |

Số kịch bản hiện tại: **36**; AC01–AC30 giữ nguyên mã để tránh phá tham chiếu. Bổ sung test concurrency Redis cho AC31, quyền replay/DTO cho AC32–34, transaction/validation cho AC35–36.

- Unit: validation, tính tiền, chuyển trạng thái, delta tồn và quyền sửa.
- Integration PostgreSQL/Redis: AC06, AC10–13, AC15–16, AC18–19, AC21–22, AC30; kiểm tra race thật bằng hai transaction, không chỉ mock repository.
- API/security: quyền, ownership, CSRF, password DTO và error contract.
- UI/end-to-end: guest → giỏ → login → COD → admin xử lý; loading/error/retry, viewport mobile/desktop.
- AI: tập 20–30 câu có dữ liệu đối chiếu, lưu kết quả đúng/sai/latency/cost, không chỉ đánh giá câu trả lời nghe hay.

Mỗi PR liên kết F/BR/AC liên quan và ghi test nào đã chạy. Không đổi TODO thành PASS khi chỉ viết tài liệu/test chưa thực thi.
