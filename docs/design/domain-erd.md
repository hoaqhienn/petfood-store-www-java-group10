# Domain Class Diagram và ERD — P0

Phạm vi gồm catalog, tài khoản, giỏ Session, checkout COD, quản lý đơn, tồn kho và email nền. Đây là thiết kế đề xuất cho V2. Các quyết định D01–D08 vẫn cần review theo [quy tắc nghiệp vụ](../BUSINESS_RULES.md).

## Domain Class Diagram

```mermaid
classDiagram
  class Account {
    normalizedEmail
    role
    status
  }
  class Category {
    slug
    status
  }
  class Product {
    sku
    price
    availableStock
  }
  class Cart {
    cartToken
    revision
  }
  class CartLine {
    productId
    quantity
  }
  class CheckoutQuote {
    customerId
    cartToken
    cartRevision
    expiresAt
  }
  class Order {
    orderCode
    status
    paymentStatus
    version
  }
  class OrderLine {
    productId
    skuSnapshot
    nameSnapshot
    unitPrice
    quantity
  }
  class DeliverySnapshot {
    recipientName
    phone
    addressText
  }
  class OrderHistory {
    action
    reason
  }
  class CheckoutReceipt {
    idempotencyKey
    fingerprint
  }
  class InventoryMovement {
    skuSnapshot
    delta
    reason
  }
  class NotificationEvent {
    eventId
    type
    status
  }

  Category "1" -- "0..*" Product : phân loại
  Cart "1" *-- "0..*" CartLine
  CartLine ..> Product : tra theo ID
  CheckoutQuote ..> Cart : token và revision
  CheckoutQuote ..> Account : chủ quote
  Account "1" -- "0..*" Order : đặt
  Order "1" *-- "1..*" OrderLine
  Order "1" *-- "1" DeliverySnapshot
  Order "1" *-- "1..*" OrderHistory
  OrderLine "0..*" --> "1" Product : tham chiếu SKU
  Account "1" -- "0..*" CheckoutReceipt : sở hữu
  CheckoutReceipt "1" --> "1" Order : kết quả đã commit
  InventoryMovement "0..*" --> "0..1" Product
  InventoryMovement "0..*" --> "0..1" Order
  Order ..> NotificationEvent : ORDER_PLACED
  Account ..> NotificationEvent : REGISTRATION
```

Guest là actor có giỏ trong Session. CUSTOMER và ADMIN là vai trò của Account. DeliverySnapshot là value object được lưu trong hàng đơn. Quote lưu riêng trong Redis có TTL và gắn với người dùng, cartToken và revision. Dòng giỏ tham chiếu product bằng ID, nên backend cần kiểm tra lại khả năng bán và tồn kho khi checkout.

## ERD PostgreSQL

```mermaid
erDiagram
  USER_ACCOUNT ||--o{ CUSTOMER_ORDER : customer
  USER_ACCOUNT ||--o{ CHECKOUT_REQUEST : owns
  USER_ACCOUNT |o--o{ ORDER_HISTORY : actor
  USER_ACCOUNT |o--o{ INVENTORY_MOVEMENT : actor
  CATEGORY ||--o{ PRODUCT : contains
  CUSTOMER_ORDER ||--|{ ORDER_ITEM : contains
  PRODUCT ||--o{ ORDER_ITEM : referenced
  CUSTOMER_ORDER ||--|{ ORDER_HISTORY : records
  CUSTOMER_ORDER ||--|| CHECKOUT_REQUEST : receipt
  PRODUCT |o--o{ INVENTORY_MOVEMENT : product
  CUSTOMER_ORDER |o--o{ INVENTORY_MOVEMENT : order

  USER_ACCOUNT {
    bigint id PK
    text email_normalized UK
    text role
    text status
    bigint version
  }
  CATEGORY {
    bigint id PK
    text slug UK
    bigint version
  }
  PRODUCT {
    bigint id PK
    bigint category_id FK
    text sku UK
    numeric price
    int available_stock
    bigint version
  }
  CUSTOMER_ORDER {
    bigint id PK
    bigint customer_id FK
    text order_code UK
    text status
    text payment_method
    text payment_status
    numeric subtotal
    numeric shipping_fee
    numeric grand_total
    timestamptz created_at
    timestamptz paid_at "nullable"
    bigint version
  }
  ORDER_ITEM {
    bigint id PK
    bigint order_id FK
    bigint product_id FK
    text sku_snapshot
    text name_snapshot
    numeric unit_price
    int quantity
    numeric line_total
  }
  ORDER_HISTORY {
    bigint id PK
    bigint order_id FK
    bigint actor_id FK "nullable"
    text action
    timestamptz created_at
  }
  CHECKOUT_REQUEST {
    bigint id PK
    bigint customer_id FK
    bigint order_id FK,UK
    text idempotency_key
    text fingerprint
    text cart_token
    bigint cart_revision
  }
  INVENTORY_MOVEMENT {
    bigint id PK
    bigint product_id FK "nullable"
    bigint order_id FK "nullable"
    bigint actor_id FK "nullable"
    text sku_snapshot
    int delta
    text reason
    timestamptz created_at
  }
  NOTIFICATION_OUTBOX {
    uuid id PK
    text type
    bigint aggregate_id "logical reference"
    jsonb payload
    text status
    int attempts
    timestamptz next_attempt_at
  }
```

Các kiểu dữ liệu là đề xuất. Sơ đồ hiển thị cột phục vụ quan hệ và toàn vẹn. Danh sách trường nghiệp vụ còn lại nằm trong [mô hình miền](../DOMAIN_MODEL.md). Bảng outbox đứng riêng vì aggregate_id trỏ logic đến Account hoặc Order theo type.

Backend ghi customer_id của receipt và đơn từ cùng tài khoản đã xác thực trong transaction. Bội số bắt buộc từ đơn đến receipt, dòng đơn và lịch sử mô tả trạng thái sau commit. Backend bảo đảm các bản ghi đi kèm được tạo cùng đơn.

| Điểm cần bảo vệ | Cách thiết kế |
|---|---|
| Xóa danh mục, sản phẩm hoặc khách đã đặt hàng | FK từ product đến category, order_item đến product và customer_order đến user dùng RESTRICT. Kiểm tra ở API để trả lỗi rõ ràng. FK bảo vệ khi có ghi đồng thời. |
| Giữ audit khi xóa hợp lệ | FK product của movement và FK actor của audit dùng SET NULL. Giữ sku_snapshot. checkout_request thuộc đơn đã tạo, nên quan hệ này không chặn xóa thêm trường hợp user chưa từng đặt hàng. |
| Chống tạo đơn trùng | UNIQUE trên (customer_id, idempotency_key), (cart_token, cart_revision) và order_id. Cùng key và fingerprint trả đơn cũ. Cùng key khác fingerprint bị từ chối. Kiểm tra replay trước khi kiểm tra TTL quote. |
| Giữ giá và địa chỉ lịch sử | UNIQUE(order_id, product_id). Đơn giữ snapshot thông tin nhận hàng, dòng đơn giữ SKU, tên và đơn giá. Sửa số lượng tính lại từ giá snapshot. |
| Nhất quán đơn và tồn | Một transaction lưu đơn, dòng, receipt, lịch sử, cập nhật tồn và ghi movement, outbox. Java kiểm tra trạng thái, số lượng, tồn và đơn phải có ít nhất một dòng. DB dùng PK/FK/UNIQUE và NOT NULL theo trường bắt buộc. |
| COD, Session và email | Đơn khởi tạo PENDING/UNPAID. Sau commit mới xóa giỏ theo đúng token/revision. Worker gửi mail từ outbox và retry độc lập. |

Ba tình huống cần đối chiếu khi review V2:

- Xóa SKU từng xuất hiện trong đơn bị chặn, kể cả đơn đã hủy. SKU chỉ có lịch sử điều chỉnh tồn vẫn xóa được và movement giữ snapshot.
- Client mất phản hồi sau commit và gửi lại cùng key. API trả cùng mã đơn, không trừ tồn hoặc ghi email event lần hai, kể cả quote đã hết hạn.
- Admin đổi giá sau checkout. Đơn đã lưu hiển thị giá mua cũ và dùng giá đó khi sửa số lượng hợp lệ.

Tiếp theo dùng ERD này để dựng Sequence Diagram UC07, đặc biệt ở nhánh replay, khóa tồn, rollback và dọn giỏ sau commit.
