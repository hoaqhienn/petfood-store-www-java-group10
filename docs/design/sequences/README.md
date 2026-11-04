# Sequence Diagram UC07 — Checkout COD

![Sequence Diagram UC07](uc07-checkout.svg)

[Nguồn PlantUML](uc07-checkout.puml) · [Activity Diagram UC07](../activities/uc07-checkout.puml) · [ERD P0](../domain-erd.md) · [API contract](../../API_CONTRACT.md)

- **Replay.** Xác thực trước khi tra receipt bằng actor + key. Receipt cùng fingerprint cho phép trả đơn cũ dù quote hết hạn hoặc giỏ đã bị dọn. Trường hợp khác fingerprint trả `IDEMPOTENCY_CONFLICT`.
- **Cạnh tranh.** Khóa SKU theo thứ tự ID. Các khóa unique trên (customer_id, idempotency_key) và (cart_token, cart_revision) bảo vệ cả trường hợp hai request dùng key khác nhau cho cùng giỏ. Nếu vi phạm unique, rollback toàn bộ trước khi tra lại bằng transaction mới. Cùng actor/key và fingerprint trả đơn cũ. Key khác cho cùng revision trả conflict, không trả thông tin đơn của actor khác. Việc tra lại khi kiểm tra giỏ, quote hoặc tồn thất bại giúp nhận ra request cạnh tranh đã commit.
- **Kết quả commit chưa rõ.** Nếu mất kết nối khi COMMIT, tra receipt khi DB khả dụng. Nếu chưa xác định được kết quả, frontend giữ nguyên body/key để retry. Khi xác nhận được đơn đã commit, lần cleanup được phép dùng compare-and-clear với token và revision gốc của giỏ hiện tại. Nếu giỏ đã đổi hoặc Redis đang lỗi thì giữ kết quả đơn. Cleanup phải giữ phiên đăng nhập.
- **Báo giá lại.** Nếu chưa có receipt và nhận `PRICE_CHANGED`, `QUOTE_EXPIRED` hoặc `CART_CHANGED`, frontend lập quote mới và dùng key mới sau khi khách xác nhận lại. Tồn chỉ thay đổi trong transaction tạo đơn. Email chạy độc lập sau commit.

D02, D05 và D08 vẫn là đề xuất. Tham chiếu BR11–15, BR19–20 và AC09–13, AC21–22, AC32, AC36.
