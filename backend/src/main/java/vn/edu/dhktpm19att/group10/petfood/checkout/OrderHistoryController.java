package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/orders")
class OrderHistoryController {
    private static final String FIELDS = """
            id, order_code, status, payment_method, payment_status,
            subtotal, shipping_fee, grand_total, created_at, paid_at, version
            """;

    private final JdbcTemplate db;

    OrderHistoryController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> list(Authentication authentication, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) return invalidPage();
        long actor = Long.parseLong(authentication.getName());
        if (!active(actor)) return locked();
        long total = db.queryForObject("SELECT count(*) FROM customer_order WHERE customer_id = ?", Long.class, actor);
        var items = db.query("SELECT " + FIELDS + """
                 FROM customer_order WHERE customer_id = ?
                 ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> summary(rs), actor, size, (long) page * size);
        return ResponseEntity.ok(new OrderPage(items, page, size, total, total / size + (total % size == 0 ? 0 : 1)));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> detail(Authentication authentication, @PathVariable String id) {
        long actor = Long.parseLong(authentication.getName());
        if (!active(actor)) return locked();
        return findDetail(id, actor);
    }

    ResponseEntity<?> findDetail(String id, Long actor) {
        if (!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return missing();
        UUID orderId = UUID.fromString(id);
        String ownerFilter = actor == null ? "" : " AND customer_id = ?";
        Object[] parameters = actor == null ? new Object[]{orderId} : new Object[]{orderId, actor};
        var orders = db.query("SELECT " + FIELDS + """
                 , recipient_name, phone, address_text FROM customer_order WHERE id = ?
                """ + ownerFilter, (rs, row) -> new DetailHeader(summary(rs),
                new Recipient(rs.getString("recipient_name"), rs.getString("phone"), rs.getString("address_text"))),
                parameters);
        if (orders.isEmpty()) return missing();

        var items = db.query("""
                SELECT i.product_id, i.sku_snapshot, i.name_snapshot, i.unit_price, i.quantity, i.line_total
                FROM order_item i WHERE i.order_id = ? ORDER BY i.product_id
                """, (rs, row) -> new Line(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4).toPlainString(), rs.getInt(5), rs.getBigDecimal(6).toPlainString()), orderId);
        var header = orders.get(0);
        var order = header.order();
        return ResponseEntity.ok(new OrderDetail(order.id(), order.orderCode(), order.status(), order.paymentMethod(),
                order.paymentStatus(), order.subtotal(), order.shippingFee(), order.grandTotal(), order.createdAt(),
                order.paidAt(), order.version(), header.recipient(), items));
    }

    private boolean active(long actor) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_account WHERE id = ?
                    AND status = 'ACTIVE' AND role IN ('CUSTOMER', 'ADMIN'))
                """, Boolean.class, actor));
    }

    static Summary summary(ResultSet rs) throws SQLException {
        var paidAt = rs.getObject("paid_at", OffsetDateTime.class);
        return new Summary(rs.getString("id"), rs.getString("order_code"), rs.getString("status"),
                rs.getString("payment_method"), rs.getString("payment_status"),
                rs.getBigDecimal("subtotal").toPlainString(), rs.getBigDecimal("shipping_fee").toPlainString(),
                rs.getBigDecimal("grand_total").toPlainString(), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                paidAt == null ? null : paidAt.toInstant(), rs.getLong("version"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<?> invalidPage() {
        return CheckoutPreviewController.error(400, "INVALID_QUERY", "Thông tin phân trang không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return CheckoutPreviewController.error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể xem đơn hàng.");
    }

    private static ResponseEntity<?> missing() {
        return CheckoutPreviewController.error(404, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.");
    }

    private static ResponseEntity<?> locked() {
        return CheckoutPreviewController.error(403, "ACCOUNT_LOCKED", "Tài khoản không thể xem đơn hàng.");
    }

    record OrderPage(List<Summary> items, int page, int size, long totalElements, long totalPages) {}
    record Summary(String id, String orderCode, String status, String paymentMethod, String paymentStatus,
            String subtotal, String shippingFee, String grandTotal, Instant createdAt, Instant paidAt, long version) {}
    record OrderDetail(String id, String orderCode, String status, String paymentMethod, String paymentStatus,
            String subtotal, String shippingFee, String grandTotal, Instant createdAt, Instant paidAt, long version,
            Recipient recipient, List<Line> items) {}
    record Recipient(String name, String phone, String address) {}
    record Line(String productId, String sku, String name, String unitPrice, int quantity, String lineTotal) {}
    private record DetailHeader(Summary order, Recipient recipient) {}
}
