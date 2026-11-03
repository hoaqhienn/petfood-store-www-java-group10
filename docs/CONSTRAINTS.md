# Danh mục ràng buộc và kết quả rà soát

Đối chiếu ngày 03/11/2026 với đề bài, [quy tắc nghiệp vụ](BUSINESS_RULES.md), [API](API_CONTRACT.md), [dữ liệu](DOMAIN_MODEL.md) và cấu hình/mã nguồn hiện có. Mã RB01–RB22 giữ đúng danh sách đã trao đổi trong chat; BR là đặc tả chi tiết, AC là kịch bản nghiệm thu.

## Trạng thái thực thi

**Đã có ở bộ khung:** CSRF đang bật, chỉ GET info/health public và các API khác bị chặn; cấu hình Redis Session 30 phút, cookie HttpOnly/SameSite=Lax; Flyway bật extension vector; Docker/CI. Điều này **chưa chứng minh** đã có login, phân quyền customer/admin, giỏ, đơn, email nghiệp vụ, AI hoặc tracking.

**Chưa thực thi nghiệp vụ:** toàn bộ RB01–RB22 dưới đây. AC là yêu cầu kiểm thử trong tương lai, không phải bằng chứng test pass. Không thêm migration/endpoint trong lần cập nhật tài liệu này.

## Ma trận ràng buộc

Nguồn: **Đề bài** = bắt buộc của môn học; **Thiết kế** = bảo đảm tính đúng/bảo mật; **Đề xuất** = chính sách cần review. Một dòng có thể có nhiều nguồn; xem D01–D08 để phân biệt các con số/chính sách chưa chốt.

| Mã | Ràng buộc | Nguồn | Nơi thực thi dự kiến | Đối chiếu |
|---|---|---|---|---|
| RB01 | Email không trùng sau chuẩn hóa, kể cả đăng ký đồng thời | Đề bài + thiết kế | Service + UNIQUE DB | BR05; AC06 |
| RB02 | Băm password, không trả/log password/hash | Đề bài + thiết kế | Encoder, DTO, log/mail | BR05–06; AC05,19 |
| RB03 | Đăng ký chỉ CUSTOMER, không nhận role từ client | Thiết kế | Registration service + DTO whitelist | BR06; AC05,33 |
| RB04 | Guest dùng giỏ nhưng phải login mới checkout | Đề bài | Security + checkout service | BR07,12; AC07,09 |
| RB05 | Customer chỉ truy cập đơn/hồ sơ thú cưng của mình | Thiết kế | Ownership query/service | BR15,21; AC14,23,32 |
| RB06 | Admin có quyền customer; quyền quản trị kiểm tra backend | Đề bài + thiết kế | Security + service | BR08,15; AC08,14 |
| RB07 | Chỉ xóa user chưa có đơn, kể cả đơn hủy | Đề bài | Transaction + FK RESTRICT | BR09; AC19 |
| RB08 | SKU duy nhất, loại hợp lệ | Thiết kế + đề xuất D01 | Service + UNIQUE/FK | BR01–02; AC18,35 |
| RB09 | Giá/trọng lượng dương, tồn nguyên không âm | Thiết kế | Java validation + stock transaction | BR02; AC11,30,35 |
| RB10 | Không xóa SKU đã có trong bất kỳ đơn nào | Đề bài | Service + FK RESTRICT | BR04; AC18 |
| RB11 | Không xóa loại còn sản phẩm, kể cả ẩn | Đề bài | Service + FK RESTRICT | BR04; AC18 |
| RB12 | Không mua sản phẩm hết hàng/ngừng bán/loại ẩn | Thiết kế | Cart/checkout; kiểm tra dưới khóa phù hợp | BR03,12–13; AC02,11,36 |
| RB13 | Đổi catalog không đổi lịch sử đơn | Thiết kế | Snapshot order_item | BR15; AC17 |
| RB14 | Giỏ trong Session/Redis, không có bảng giỏ | Đề bài + stack đã chốt | Session repository | BR10; AC03,31 |
| RB15 | Quantity nguyên dương, checkout giỏ không rỗng | Thiết kế | Validation backend; UI hỗ trợ | BR10,12; AC04,09 |
| RB16 | Giá/tổng từ backend, không tin client | Thiết kế | Quote/order service | BR11–12; AC09 |
| RB17 | Người nhận/điện thoại/địa chỉ hợp lệ | Thiết kế | DTO + service + UI | BR12; AC09,35 |
| RB18 | Đơn/dòng hàng/tồn/outbox commit hoặc rollback cùng nhau | Thiết kế | PostgreSQL transaction | BR13,19; AC10–11,21 |
| RB19 | Không bán vượt tồn khi đồng thời | Thiết kế | Khóa/thao tác stock trong transaction | BR13,17; AC11,16,30 |
| RB20 | Retry không tạo/trừ tồn thêm lần nữa | Thiết kế | Idempotency + UNIQUE + ownership | BR14; AC12–13,32 |
| RB21 | Xóa giỏ đã mua, giữ login và giỏ revision mới | Đề bài + thiết kế | Cleanup sau commit | BR14; AC10,13,31 |
| RB22 | Không giả định PostgreSQL/Redis atomic | Thiết kế | Reconcile/retry, không rollback đơn đã commit | BR14; AC13,22 |

## Nhóm ràng buộc bổ sung

| Nhóm | Điều kiện | Đối chiếu |
|---|---|---|
| COD | Đặt hàng chưa phải đã thu tiền; đúng state machine, không hoàn tồn hai lần, sửa quantity phải cập nhật delta/tổng/version | BR16–18; AC15–17,20 |
| Email | Gửi sau commit, retry không nhân đôi đơn/user; không hứa exactly-once SMTP | BR19–20; AC21 |
| AI | Chỉ sản phẩm có thật; lọc theo nhu cầu; kiểm tra lại giá/tồn; không có quyền SQL/ghi đơn; model/dimension nhất quán | BR22–23; AC24–26 |
| Tracking | Consent theo mục đích, không PII trong events/vector, dedupe, event đơn từ backend; dữ liệu demo/tập đo rõ ràng | BR24–26; AC27–29 |
| Thú cưng | Ownership, tên/loài bắt buộc, không bắt buộc để mua | BR21; AC23 |
| Công nghệ | Java/Spring Boot/Gradle, React, PostgreSQL, Redis, Docker; không tự viết database function/CHECK/stored procedure | REQUIREMENTS; kiểm tra migration khi triển khai |
| Dữ liệu | PK/FK/UNIQUE được phép; Hibernate validate, Flyway quản lý schema; không cascade xóa đơn theo user/product | DOMAIN_MODEL; AC18–19 |
| Bảo mật vận hành | Không commit secret; API key ở backend; không lộ PII qua log/error; route quản trị không dựa vào UI để bảo vệ | API_CONTRACT; AC08,19,33–34 |

## Giới hạn cấu hình và chính sách chưa chốt

| Mục | Hiện trạng |
|---|---|
| Session 30 phút; SMTP timeout 5 giây | Đã cấu hình trong application.yml, không phải yêu cầu đề bài |
| Cookie Secure=false mặc định | Chỉ cho HTTP local; production HTTPS phải bật Secure |
| Tối đa 50 SKU, 1–99/dòng, quote 10 phút, mail retry 3 lần | Đề xuất D08, chưa thực thi |
| Trừ tồn PENDING, hủy/sửa trước SHIPPING | Đề xuất D02–D04, chưa thực thi |
| Ship demo=0; chưa bắt buộc xác minh email | Đề xuất D05–D06, chưa thực thi |
| Tracking opt-in, raw 90 ngày | Đề xuất D07; không phải kết luận pháp lý |
| Password 8–64 ký tự, tối đa 3 gợi ý AI, size list tối đa 100 | Giới hạn thiết kế dự thảo; cần review encoder/UX/quota |
| Độ dài tên/SKU/địa chỉ, precision tiền, giới hạn stock/event batch/chat | Cần chốt trước DTO/migration liên quan; chưa có con số chính thức |
| Hiệu năng, số người đồng thời, ngân sách/timeout AI | Chưa có SLO được thống nhất; không tuyên bố chịu tải hay độ trễ chưa đo |

## Bổ sung sau rà soát

1. **Giỏ nhiều tab:** expectedRevision phải được kiểm tra và cập nhật nguyên tử; đọc Session rồi so revision trong Java và ghi lại không đủ chống lost update. Trả 409 cho revision cũ. Cleanup cũng cần compare-and-clear nguyên tử, không xóa giỏ mới.
2. **Replay checkout:** luôn kiểm tra actor ACTIVE/ownership trước lookup key; key không phải credential. Replay trước kiểm tra giỏ/quote không có nghĩa bỏ qua authentication. Đổi tài khoản trong cùng trình duyệt không được nhận đơn của người trước.
3. **Admin khởi tạo:** bootstrap qua cấu hình local riêng, không qua đăng ký public, không commit password mặc định. Bootstrap phải idempotent, không reset mật khẩu khi restart. Chặn request cố mass-assign role/status/stock/giá đơn.
4. **API authentication:** 401/403 của API_CONTRACT là mục tiêu tương lai. Bộ khung hiện denyAll với route chưa triển khai, không phải cơ chế login/role hoàn chỉnh.
5. **Xóa product có movement:** product_id phải nullable ngay trong thiết kế, có sku_snapshot và SET NULL; vẫn giữ FK RESTRICT của order_item. Xóa audit actor chỉ làm mất liên kết cá nhân, không sửa nội dung giao dịch.

Đã bổ sung AC31–AC36 để kiểm chứng các khoảng trống này khi viết code. Chi tiết BR vẫn là nguồn quy tắc; bảng này dùng tra cứu nguồn, nơi thực thi và coverage.
