# Danh mục và đặc tả use case — 04/11/2026

Giai đoạn 1 của thiết kế UML: mô tả mục tiêu và hành vi quan sát được, chưa phải chức năng đã triển khai. Tham chiếu [yêu cầu](REQUIREMENTS.md), [nghiệp vụ](BUSINESS_RULES.md), [ràng buộc](CONSTRAINTS.md), [nghiệm thu](ACCEPTANCE.md). D01–D08 vẫn là phương án thiết kế cần review; yêu cầu bắt đầu thiết kế không tự chuyển chúng thành chính sách đã phê duyệt.

## Actor và biên hệ thống

- Người truy cập: actor khái quát dùng cho xem catalog/giỏ; Guest và Customer là hai chuyên biệt. Không cho Customer kế thừa Guest vì sẽ kéo theo đăng ký tài khoản không phù hợp trạng thái.
- Guest: chưa xác thực; có thể đăng ký/đăng nhập.
- Customer: tài khoản đã xác thực, ACTIVE; thao tác dữ liệu của mình.
- Admin: chuyên biệt của Customer, thêm quyền quản trị; API mua hàng vẫn giới hạn ownership.
- SMTP: hệ thống ngoài hỗ trợ gửi email; AI API: hệ thống ngoài hỗ trợ suy luận/embedding ở P1.
- React, Spring Boot, PostgreSQL, Redis và worker nằm trong biên PetFood; không phải actor nghiệp vụ. Chưa có actor cổng thanh toán online hoặc hãng vận chuyển tích hợp.

## Danh mục và truy vết

| UC | Mục tiêu | Actor chính | Mức | F | BR | AC |
|---|---|---|---|---|---|---|
| UC01 | Tìm/xem sản phẩm | Người truy cập | P0 | F01 | BR01–03 | AC01–02 |
| UC02 | Quản lý giỏ | Người truy cập | P0 | F02 | BR10,14 | AC03–04,31 |
| UC03 | Đăng ký | Guest | P0 | F03,10 | BR05–06,19–20 | AC05–06,21,33 |
| UC04 | Đăng nhập | Guest | P0 | F04 | BR07–08 | AC07–08 |
| UC05 | Đăng xuất | Customer | P0 | F04 | BR07 | AC07 |
| UC06 | Cập nhật hồ sơ cá nhân | Customer | P0 | F04 | BR08 | AC08,33 |
| UC07 | Đặt hàng COD | Customer | P0 | F05,10 | BR11–15,19–20 | AC09–13,21–22,32,36 |
| UC08 | Tra cứu đơn của tôi | Customer | P0 | F06 | BR15,18 | AC14,20 |
| UC09 | Hủy đơn của tôi | Customer | P0 đề xuất | F06 | BR16–17 | AC15 |
| UC10 | Quản lý sản phẩm | Admin | P0 | F07 | BR01–04 | AC18,33,35–36 |
| UC11 | Quản lý loại sản phẩm | Admin | P0 | F07 | BR03–04 | AC18,36 |
| UC12 | Điều chỉnh tồn | Admin | P0 đề xuất | F07 | BR02,13 | AC30 |
| UC13 | Quản lý tài khoản | Admin | P0 | F08 | BR08–09 | AC19,33 |
| UC14 | Tra cứu mọi đơn | Admin | P0 | F09 | BR15,18 | AC20 |
| UC15 | Sửa số lượng trong đơn | Admin | P0 | F09 | BR16–17 | AC16–17 |
| UC16 | Xử lý trạng thái đơn | Admin | P0 | F09 | BR16–18 | AC15,17,20 |
| UC17 | Kiểm tra/retry email lỗi | Admin | P0 hỗ trợ | F10 | BR19–20 | AC21 |
| UC18 | Quản lý hồ sơ thú cưng | Customer | P1 | F11 | BR21 | AC23 |
| UC19 | Tìm kiếm ngữ nghĩa | Người truy cập | P1 | F12 | BR22–23 | AC24–26 |
| UC20 | Nhận tư vấn AI | Người truy cập | P1 | F13 | BR21–23 | AC23–26 |
| UC21 | Chọn quyền tracking/cá nhân hóa | Người truy cập | P1 | F14–15 | BR24–26 | AC27,29 |
| UC22 | Xem gợi ý sản phẩm | Người truy cập | P1 | F15 | BR22–26 | AC24,29 |
| UC23 | Xem phân tích hành vi | Admin | P1 | F14 | BR25–26 | AC28 |

UC10/11/13 là nhóm CRUD để sơ đồ tổng quan không quá lớn; đặc tả bên dưới phân biệt tìm/list/detail/create/update/delete và điều kiện xóa. Gửi mail nền, khởi tạo admin, ghi event và tạo embedding là hành vi hỗ trợ; không giả khách chủ động kích hoạt các tác vụ nền.

## Quy ước đặc tả

Mọi UC có actor đã xác thực đều kiểm tra quyền/trạng thái tại backend mỗi request; lỗi quyền không thay đổi dữ liệu. Mutation kiểm tra CSRF. Trường hợp thất bại không làm lộ dữ liệu của người khác. Các bước đánh số là luồng chính; ngoại lệ tham chiếu bước hoặc thao tác liên quan. AC vẫn TODO, không coi là test đã pass.

### UC01 — Tìm/xem sản phẩm

- Kích hoạt: mở cửa hàng hoặc nhập tiêu chí tìm kiếm. Tiền điều kiện: không cần login.
- Luồng: (1) nhập từ khóa/bộ lọc; (2) hệ thống lấy các SKU/loại ACTIVE, phân trang; (3) khách chọn SKU; (4) hiển thị chi tiết, giá và khả dụng.
- Ngoại lệ: bước 2 không có kết quả → empty state; bước 3 SKU bị ẩn/xóa → không tìm thấy; hết hàng → vẫn xem được nhưng không cho mua; DB lỗi → thông báo thử lại.
- Hậu điều kiện: không thay đổi tồn hoặc giỏ. Khách có thể khởi động UC02 độc lập từ list/detail.

### UC02 — Quản lý giỏ

- Kích hoạt: thêm hàng hoặc mở giỏ. Tiền điều kiện: Session khả dụng; không cần login.
- Luồng: (1) đọc giỏ/revision; (2) chọn thêm, đổi quantity hoặc xóa dòng; (3) kiểm tra quantity/SKU/tồn và revision; (4) cập nhật giỏ nguyên tử; (5) trả giỏ theo giá hiện hành.
- Ngoại lệ: bước 3 revision cũ → 409 và tải lại; số lượng sai/hết hàng → giữ giỏ; Redis lỗi → thông báo, không tạo giỏ thay thế; SKU đã ẩn trong giỏ → đánh dấu không mua được.
- Hậu điều kiện: chỉ Session thay đổi, không giữ/trừ tồn. Xóa đúng revision để tránh mất thay đổi tab khác. Hết phiên không đảm bảo giữ giỏ; login giữ giỏ qua đổi session ID.

### UC03 — Đăng ký

- Kích hoạt: gửi form đăng ký. Tiền điều kiện: Guest, token CSRF hợp lệ.
- Luồng: (1) nhập tên/email/password; (2) kiểm tra/chuẩn hóa email và dữ liệu; (3) băm password; (4) transaction tạo CUSTOMER và email event; (5) thông báo thành công; (6) worker gửi email qua SMTP sau commit.
- Ngoại lệ: bước 2 email trùng/field sai → lỗi trường; bước 4 cạnh tranh UNIQUE → chỉ một tài khoản; transaction lỗi → không tạo một phần; bước 6 SMTP lỗi → tài khoản vẫn tồn tại, email chờ retry.
- Hậu điều kiện: tài khoản ACTIVE theo D06; chưa tự đăng nhập; không trả/gửi password/hash. Không có nhánh tự chọn ADMIN.

### UC04 — Đăng nhập

- Kích hoạt: gửi thông tin login. Tiền điều kiện: Guest có tài khoản, CSRF hợp lệ.
- Luồng: (1) nhập email/password; (2) xác minh credentials và ACTIVE; (3) đổi session ID, giữ giỏ; (4) trả hồ sơ/quyền; (5) UI trở lại trang trước, lấy token CSRF mới.
- Ngoại lệ: sai credentials → lỗi không tiết lộ email tồn tại; bị khóa → từ chối sau xác minh phù hợp; Session lỗi → không báo login thành công; giỏ chưa có thì bắt đầu giỏ rỗng.
- Hậu điều kiện: phiên xác thực mới; không tự gửi lại checkout. Login là tiền điều kiện của UC07, không phải include bắt buộc mỗi lần mua.

### UC05 — Đăng xuất

- Kích hoạt: chọn logout. Tiền điều kiện: có phiên đăng nhập.
- Luồng: (1) gửi logout có CSRF; (2) hủy phiên và giỏ theo BR07; (3) UI xóa trạng thái cá nhân và trở thành Guest.
- Ngoại lệ: phiên đã hết → đưa UI về Guest; không bảo đảm logout thành công nếu backend không nhận được yêu cầu, UI cần báo lỗi.
- Hậu điều kiện: Session cũ không truy cập được tài nguyên bảo vệ sau logout thành công.

### UC06 — Cập nhật hồ sơ cá nhân

- Kích hoạt: mở hồ sơ/lưu thay đổi. Tiền điều kiện: Customer ACTIVE.
- Luồng: (1) đọc hồ sơ mình/version; (2) sửa tên/điện thoại; (3) validate/kiểm tra version; (4) lưu và trả version mới.
- Ngoại lệ: field sai → giữ dữ liệu; version cũ → 409; cố sửa role/email/status → từ chối. Không nhận userId để chuyển owner.
- Hậu điều kiện: hồ sơ thay đổi, snapshot đơn cũ không đổi.

### UC07 — Đặt hàng COD

- Kích hoạt: chọn thanh toán. Tiền điều kiện: Customer ACTIVE, giỏ có hàng; Guest được mời chạy UC04 trước khi bắt đầu UC này.
- Luồng: (1) đọc giỏ, nhập thông tin nhận hàng; (2) hệ thống lập quote từ giá hiện hành; (3) khách xem tổng và xác nhận COD; (4) gửi body cố định cùng Idempotency-Key; (5) kiểm tra actor, ownership, replay, quote/revision và dữ liệu; (6) transaction kiểm tra SKU/loại/tồn, lưu đơn PENDING/UNPAID, dòng snapshot, trừ tồn, lịch sử và outbox; (7) commit; (8) xóa giỏ đúng revision đã mua; (9) trả mã đơn và “đặt hàng thành công”; (10) worker gửi email sau commit.
- Ngoại lệ 5: quote/giá/giỏ đổi → xem lại và xác nhận ý định mới; cùng key/body đã thành công → trả đơn cũ; khác body → 409; bị khóa/đổi tài khoản → không trả đơn của người cũ.
- Ngoại lệ 6: thiếu một dòng tồn/SKU ẩn/transaction lỗi → rollback toàn bộ, giữ giỏ. Hai khách mua đơn vị cuối chỉ một người thành công.
- Ngoại lệ 8: Redis lỗi → đơn vẫn thành công, retry cleanup/reconcile; tab khác có giỏ mới → giữ giỏ mới. Mất response sau commit → retry cùng key, không lập đơn mới.
- Ngoại lệ 10: email lỗi → retry event, không rollback/nhân đôi đơn.
- Hậu điều kiện: một đơn duy nhất cho ý định mua, tồn không âm, chưa thu tiền COD; login vẫn còn. D02/D05/D08 còn là giả định thiết kế.

### UC08 — Tra cứu đơn của tôi

- Kích hoạt: mở lịch sử/chi tiết. Tiền điều kiện: Customer ACTIVE.
- Luồng: (1) yêu cầu danh sách mình theo ngày; (2) phân trang; (3) chọn đơn; (4) hiển thị snapshot, trạng thái và tổng.
- Ngoại lệ: không có đơn → empty state; ID không tồn tại/không thuộc mình → 404.
- Hậu điều kiện: chỉ đọc, không thay đổi tồn/trạng thái.

### UC09 — Hủy đơn của tôi

- Kích hoạt: xác nhận hủy với lý do. Tiền điều kiện: chủ đơn ACTIVE, đơn PENDING theo D04.
- Luồng: (1) gửi ID/version/lý do; (2) kiểm tra owner, khóa đơn và kiểm tra lại trạng thái/version; (3) transaction chuyển CANCELLED, hoàn tồn đúng quantity hiện tại, ghi lịch sử; (4) trả kết quả.
- Ngoại lệ: đã CONFIRMED → từ chối; version cũ/đang bị sửa → 409; đã hủy → không hoàn tồn thêm; không thuộc owner → 404.
- Hậu điều kiện: đơn còn trong lịch sử, UNPAID, không xóa order_item.

### UC10 — Quản lý sản phẩm

- Kích hoạt: admin mở catalog. Tiền điều kiện: Admin ACTIVE.
- Luồng: (1) tìm/list/detail gồm SKU ẩn; (2) chọn tạo hoặc sửa; (3) nhập các trường cho phép, loại hợp lệ/version khi sửa; (4) validate và lưu; (5) trả chi tiết mới.
- Nhánh xóa tại 2: chọn xóa → kiểm tra không có order_item → xóa hợp lệ, movement giữ snapshot với product_id=null. Có đơn kể cả hủy → 409, có thể chọn ẩn thay thế.
- Ngoại lệ: SKU trùng, giá/trọng lượng sai, loại không tồn tại → không lưu; version cũ → 409; cố ghi đè tồn qua form sửa → từ chối, dùng UC12. Tạo mới đề xuất stock=0, nhập tồn qua UC12.
- Hậu điều kiện: catalog thay đổi, đơn cũ không đổi; P1 lập tác vụ cập nhật vector nếu đổi nội dung.

### UC11 — Quản lý loại sản phẩm

- Kích hoạt/tiền điều kiện: admin mở loại, ACTIVE.
- Luồng: (1) tìm/list/detail; (2) chọn tạo/sửa tên, slug, trạng thái/version; (3) validate và lưu.
- Nhánh xóa: chỉ xóa khi không có SKU nào, kể cả ẩn; còn SKU → 409. Slug trùng/version cũ → giữ dữ liệu.
- Hậu điều kiện: loại ẩn làm các SKU thuộc nó không mua được; không xóa SKU. Checkout cạnh tranh phải tuân thứ tự đã đồng bộ theo AC36.

### UC12 — Điều chỉnh tồn

- Kích hoạt: admin nhập chênh lệch nhập/xuất. Tiền điều kiện: SKU tồn tại, Admin ACTIVE.
- Luồng: (1) lấy stock/version; (2) nhập delta và lý do; (3) transaction kiểm tra version/tồn kết quả không âm; (4) cập nhật stock và movement; (5) trả tồn mới.
- Ngoại lệ: delta không nguyên, thiếu lý do, tồn âm/version cũ → không cập nhật. Retry version cũ không được trừ thêm.
- Hậu điều kiện: có audit, không sửa các đơn cũ. Đây là chức năng bổ sung đề xuất để quản trị tồn có kiểm soát.

### UC13 — Quản lý tài khoản

- Kích hoạt: admin mở quản lý user. Tiền điều kiện: Admin ACTIVE.
- Luồng: (1) tìm/list/detail không password/hash; (2) chọn sửa tên/điện thoại/trạng thái; (3) kiểm tra trường, version và bảo vệ admin; (4) lưu; (5) tài khoản bị khóa mất quyền từ request tiếp theo.
- Nhánh xóa: chỉ user chưa có đơn bất kỳ; xóa phụ thuộc cá nhân, xử lý audit/outbox, thu hồi Session. Nếu phát sinh đơn đồng thời → không xóa.
- Ngoại lệ: tự khóa/xóa admin hiện tại hoặc admin ACTIVE cuối → từ chối; có đơn hủy cũng không xóa; role/email ngoài scope → từ chối.
- Hậu điều kiện: không mất đơn; dữ liệu cá nhân phụ thuộc được xử lý theo BR09.

### UC14 — Tra cứu mọi đơn

- Kích hoạt/tiền điều kiện: Admin ACTIVE mở đơn hàng.
- Luồng: (1) nhập mã/khách/ngày/trạng thái; (2) trả list theo createdAt,ID giảm dần; (3) chọn chi tiết; (4) hiển thị snapshot/lịch sử/version.
- Ngoại lệ: khoảng ngày sai → lỗi trường; rỗng → empty state; đơn không tồn tại → 404.
- Hậu điều kiện: chỉ đọc, phạm vi mọi đơn chỉ qua API admin.

### UC15 — Sửa số lượng trong đơn

- Kích hoạt: admin lưu quantity mới. Tiền điều kiện: đơn PENDING/CONFIRMED theo D03.
- Luồng: (1) gửi orderId,itemId,quantity,reason,version; (2) khóa/kiểm tra item thuộc đơn và trạng thái/version; (3) tính delta, kiểm tra tồn/ACTIVE nếu tăng; (4) transaction cập nhật quantity, tồn, tổng, version và lịch sử; (5) trả đơn mới.
- Ngoại lệ: quantity=0/sai, thiếu tồn, tăng SKU ẩn, version cũ hoặc SHIPPING/trạng thái cuối → rollback. Không thêm/xóa dòng bằng UC này.
- Hậu điều kiện: giữ giá snapshot; tổng và tồn nhất quán. Không gửi lại email đặt hàng như thể tạo đơn mới.

### UC16 — Xử lý trạng thái đơn

- Kích hoạt: admin xác nhận chuyển trạng thái. Tiền điều kiện: Admin ACTIVE, đơn tồn tại.
- Luồng: (1) lấy version/trạng thái; (2) chọn bước kế tiếp theo BR16; (3) xác nhận lý do và đã thu COD nếu hoàn thành; (4) transaction kiểm tra lại, đổi trạng thái và ghi lịch sử; (5) trả kết quả.
- Nhánh hủy: PENDING/CONFIRMED → CANCELLED, hoàn tồn một lần. Nhánh hoàn thành: SHIPPING → COMPLETED và PAID/paidAt khi đã giao/thu đủ.
- Ngoại lệ: nhảy/quay lui, thiếu xác nhận COD hoặc version cũ → từ chối; giao thất bại chưa có luồng hoàn hàng, không giả hoàn thành/hủy.
- Hậu điều kiện: không sửa đơn trạng thái cuối; chỉ COMPLETED/PAID được tính doanh thu đã thu.

### UC17 — Kiểm tra/retry email

- Kích hoạt: admin mở danh sách thông báo lỗi. Tiền điều kiện: Admin ACTIVE.
- Luồng: (1) xem trạng thái/lỗi đã che PII; (2) chọn event FAILED; (3) kiểm tra event còn hợp lệ, đưa lại hàng đợi; (4) worker gửi theo cơ chế retry; (5) cập nhật trạng thái.
- Ngoại lệ: tài khoản đã xóa → không gửi mail đăng ký; event đang xử lý/SENT → không enqueue tùy tiện; SMTP lỗi → FAILED sau giới hạn đề xuất, không lập lại user/đơn.
- Hậu điều kiện: giữ event identity; có thể nhận email trùng khi SMTP mất xác nhận, không hứa exactly-once.

## P1 — đặc tả khung (chi tiết sau P0)

| UC | Tiền điều kiện / kích hoạt | Luồng chính | Ngoại lệ / hậu điều kiện |
|---|---|---|---|
| UC18 | Customer ACTIVE mở pet | List → tạo/sửa/xóa hồ sơ của mình → validate tên/loài → lưu | ID người khác 404; dữ liệu tùy chọn sai không lưu; xóa không ảnh hưởng đơn |
| UC19 | Người truy cập gửi query, còn quota | Phân tích điều kiện → truy xuất → lọc/đọc lại DB → kết quả | Không phù hợp trả rỗng; AI lỗi fallback tìm thường; không ghi đơn |
| UC20 | Người truy cập gửi câu hỏi | Chọn pet tùy chọn → kiểm tra owner → hỏi rõ nhu cầu → retrieve → trả tối đa 3 sản phẩm/lý do/link | Không có căn cứ thì nói thiếu; không chẩn đoán bệnh; timeout/quota có fallback; không tự mua |
| UC21 | Người truy cập mở preferences | Xem lựa chọn → bật/tắt theo mục đích → lưu version → áp dụng | Chưa chọn mặc định không thu optional tracking; rút quyền dọn dữ liệu liên quan, không xóa đơn |
| UC22 | Mở khu vực gợi ý | Kiểm tra consent/context → xếp hạng → đối chiếu DB → hiển thị | Không lịch sử/không consent dùng phổ biến; không lộ hồ sơ người khác |
| UC23 | Admin ACTIVE mở dashboard | Chọn ngày → tổng hợp events hợp lệ → hiển thị funnel/CTR và tập đo | Không dữ liệu thì rỗng, không giả số liệu; gắn nhãn demo; không suy tương quan thành nhân quả |

Provider/model/quota/retention chi tiết còn mở. Ghi event chỉ khi có consent phù hợp; đơn thành công lấy từ backend, không nhận event mua thành công do client khai báo.

## Lộ trình sơ đồ tiếp theo

1. Use Case Diagram tổng quan: nguồn PlantUML và bản xem nhanh ở [design/README.md](design/README.md).
2. Activity: đã có [8 sơ đồ P0 và email nền](design/activities/README.md), gồm UC03,04,02,07,09,15,16 và nhánh xóa UC10/11/13; nguồn PlantUML và SVG.
3. Domain Class + ERD: đối chiếu DOMAIN_MODEL với UC và quyết định D01–D08; chưa là DDL cuối.
4. Sequence: UC07 thành công/retry/lỗi, UC03 SMTP lỗi, UC15 cạnh tranh; xác định transaction và ranh giới Redis.
5. Design Class: rút trách nhiệm controller/service/repository từ sequence, rồi chốt API/migration.

Activity bước 2 là bản thiết kế dự thảo; bước 3–5 chưa hoàn thành. Không cần chờ P1 chi tiết để triển khai P0 đã review.
