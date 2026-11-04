# Hợp đồng API dự thảo — 03/11/2026

Đã triển khai GET `/api/v1/info`, `/actuator/health` và ba route đọc catalog bên dưới. Các route nghiệp vụ khác là hợp đồng dự thảo. Gốc nghiệp vụ `/api/v1`; Session cookie HttpOnly và CSRF cho mutation. Frontend gọi qua same-origin proxy.

## Quy ước

- JSON; id opaque string, datetime ISO-8601 UTC, tiền VND là chuỗi số nguyên (`"250000"`) để tránh làm tròn JS. Display giá do frontend định dạng, không tính tổng để quyết định thanh toán.
- List: `page` từ 0, `size` mặc định 20/tối đa 100, sort theo whitelist. Response `{items, page, size, totalElements, totalPages}`. Admin orders mặc định `createdAt,desc` rồi `id,desc`.
- Thành công: GET/PATCH 200, tạo resource 201, DELETE/logout 204. POST command có payload trả 200; checkout mới 201, replay 200.
- Lỗi: `{code, message, fieldErrors: [{field,message}], traceId}`; không stack trace/secret/PII. 400 JSON/query sai; 401 chưa login; 403 role/CSRF không hợp lệ; 404 không tồn tại hoặc không thuộc owner; 409 xung đột; 422 validation nghiệp vụ; 429 quota; 503 dịch vụ phụ thuộc chưa sẵn sàng.
- Các update có version; request body không có userId/role tùy ý để quyết định actor. Backend lấy actor từ Session. Quyền customer áp dụng cả admin đang mua cho chính mình.
- Các mã 401/403 ở trên là hợp đồng khi có identity; bộ khung hiện chỉ denyAll route chưa triển khai. DTO whitelist phải từ chối trường nhạy cảm ngoài quyền ghi, không tự bind entity từ body.
- Cart mutation kiểm tra expectedRevision/cập nhật nguyên tử; sau DELETE 204 frontend đọc lại GET /cart để lấy revision mới. Replay order cũng kiểm tra user ACTIVE/ownership trước tra cứu key; key không thay thế authentication.

## P0 — catalog và identity

| Method / route | Quyền | Dữ liệu / kết quả chính |
|---|---|---|
| GET /products | Public | q tìm theo tên/SKU, categoryId, species, lifeStage, minPrice, maxPrice, page, size. Sort: name,asc (mặc định), name,desc, price,asc, price,desc. Sản phẩm và danh mục phải ACTIVE. |
| GET /products/{id} | Public | Chi tiết, availableStock, metadata đã duyệt; không thấy sản phẩm ẩn |
| GET /categories | Public | Loại ACTIVE |
| GET /auth/csrf | Public | Token/header name cho mutation; lấy lại sau login/logout |
| POST /auth/register | Guest | displayName,email,password; 201 với trạng thái email PENDING, không hash/auto-ADMIN |
| POST /auth/login | Guest | email,password; 200 user summary, giữ giỏ và đổi session ID |
| POST /auth/logout | Đã login | Hủy phiên/giỏ, 204 |
| GET /me | Đã login | Hồ sơ và quyền của chính mình |
| PATCH /me | Đã login | displayName,phone,version; email/role không sửa trong v1 |

## P0 — giỏ và đơn của tôi

| Method / route | Quyền | Nội dung |
|---|---|---|
| GET /cart | Public Session | cartToken, revision, items theo giá hiện hành, subtotal, lỗi availability từng dòng |
| POST /cart/items | Public Session | productId, quantity, expectedRevision; cộng số lượng theo BR10 |
| PATCH /cart/items/{productId} | Public Session | quantity tuyệt đối, expectedRevision |
| DELETE /cart/items/{productId}?expectedRevision=… | Public Session | Xóa dòng, 204; revision stale trả 409 |
| POST /checkout/preview | Customer | cartRevision; báo giá, quoteToken và expiresAt; không giữ hàng |
| POST /orders | Customer | Idempotency-Key header + cartToken/cartRevision,quoteToken,recipient,paymentMethod=COD |
| GET /orders | Customer | Chỉ đơn của actor, phân trang |
| GET /orders/{id} | Chủ đơn | Snapshot, tổng, trạng thái, version; ID người khác trả 404 |
| POST /orders/{id}/cancel | Chủ đơn | reason,version; chỉ PENDING, trả đơn cập nhật |

Ví dụ đặt đơn (không đưa giá/tổng từ client làm nguồn sự thật):

```json
{
  "cartToken": "cart-example",
  "cartRevision": 3,
  "quoteToken": "quote-example",
  "paymentMethod": "COD",
  "recipient": {
    "name": "Khach demo",
    "phone": "0900000000",
    "address": "Dia chi demo, khong phai don giao that"
  }
}
```

Frontend giữ key và body khi mất mạng, retry đúng request. PRICE_CHANGED/QUOTE_EXPIRED/CART_CHANGED yêu cầu preview mới và **ý định mua mới/key mới** sau khách xác nhận. Backend đã commit thì replay cùng key trả order trước khi kiểm tra quote hết hạn/giỏ đã xóa. UI không hiển thị “thanh toán thành công” cho COD chưa thu tiền.

## P0 — admin

Mọi route dưới đây yêu cầu ADMIN ACTIVE.

| Method / route | Nội dung / quy tắc |
|---|---|
| GET /admin/products; GET /admin/products/{id} | Tìm q/sku/category/status, list gồm cả ẩn; xem chi tiết |
| POST /admin/products; PATCH /admin/products/{id} | Thêm/sửa trường catalog, version; không sửa tồn trực tiếp qua update thông thường |
| POST /admin/products/{id}/stock-adjustments | delta nguyên có dấu,reason,version; transaction, không làm tồn âm; ghi audit |
| DELETE /admin/products/{id} | 409 PRODUCT_REFERENCED nếu có dòng đơn bất kỳ |
| GET /admin/categories; GET /admin/categories/{id} | Tìm tên/status, danh sách và chi tiết |
| POST /admin/categories; PATCH /admin/categories/{id} | name,slug,status,version |
| DELETE /admin/categories/{id} | 409 CATEGORY_NOT_EMPTY nếu còn SKU, kể cả ẩn |
| GET /admin/users; GET /admin/users/{id} | q/status/role, danh sách/chi tiết không password/hash |
| PATCH /admin/users/{id} | displayName,phone,status,version; không tự khóa admin hiện tại/cuối cùng |
| DELETE /admin/users/{id} | 409 USER_HAS_ORDERS nếu đã đặt dù hủy; bảo vệ admin |
| GET /admin/orders; GET /admin/orders/{id} | orderCode/customer/dateFrom/dateTo/status, sắp xếp ngày mua |
| PATCH /admin/orders/{id}/items/{itemId} | quantity,reason,version; itemId phải thuộc đơn; giữ giá snapshot |
| POST /admin/orders/{id}/transitions | toStatus,reason,version; COMPLETED thêm codReceived=true; không nhảy trạng thái |
| GET /admin/notifications; POST /admin/notifications/{id}/retry | Xem trạng thái/lỗi đã che dữ liệu; retry FAILED, không tạo lại đơn/user |

Đề xuất bổ sung điều chỉnh tồn có lý do thay cho ghi đè stock từ form sản phẩm; chỉ đưa vào P0 sau review. Với thao tác sửa đơn, khóa order trước. Các luồng cần kiểm tra cả danh mục và SKU khóa danh mục theo ID tăng dần, rồi SKU theo ID tăng dần. Xem [thứ tự khóa catalog](design/sequences/README.md#khóa-catalog-và-ac36). Version không thay thế transaction.

## P1 — điểm mở rộng

| Route dự kiến | Phạm vi |
|---|---|
| GET/POST /me/pets; GET/PATCH/DELETE /me/pets/{id} | Chủ hồ sơ, validate tên/loài và dữ liệu tùy chọn |
| GET/PUT /consent | Theo Session và user nếu có; analytics/personalization riêng, ghi phiên bản lựa chọn |
| POST /search/semantic | Public có quota; query + filters, trả sản phẩm đã kiểm tra DB |
| POST /assistant/messages | Public có quota; message, petId tùy chọn (chỉ owner), trả answer/sources/productIds/fallback |
| GET /recommendations | Theo consent; sản phẩm phổ biến fallback; backend cấp recommendationId/impressionId |
| POST /events | Session; whitelist + consent; batch có giới hạn, dedupe eventId; không chấp nhận client tạo order_placed |
| GET /admin/analytics | ADMIN; khoảng ngày, funnel/CTR, định nghĩa denominator, cờ dữ liệu demo |

Quota, provider, model và schema event chi tiết chốt ở P1. Không mở route AI với quyền thực thi SQL hoặc ghi đơn.

## Mã lỗi cần dùng thống nhất

EMAIL_EXISTS, INVALID_CREDENTIALS, ACCOUNT_LOCKED, CSRF_INVALID, PRODUCT_UNAVAILABLE, INSUFFICIENT_STOCK, INVALID_QUANTITY, CART_CHANGED, PRICE_CHANGED, QUOTE_EXPIRED, IDEMPOTENCY_CONFLICT, VERSION_CONFLICT, INVALID_ORDER_TRANSITION, PRODUCT_REFERENCED, CATEGORY_NOT_EMPTY, USER_HAS_ORDERS, ADMIN_PROTECTED, DEPENDENCY_UNAVAILABLE.

Thông báo login không tiết lộ tài khoản có tồn tại hay không. Error code account locked chỉ trả sau xác minh credentials phù hợp. Luồng bảo mật thực tế phải map lỗi Spring Security vào cùng format khi triển khai.
