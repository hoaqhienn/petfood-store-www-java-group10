package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/** Admin commands that change an order: status transitions (BR16) and quantity edits (BR17). */
@RestController
@RequestMapping("/api/v1/admin/orders")
class AdminOrderCommandController {
    // Allowed next statuses. COMPLETED and CANCELLED are final.
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", Set.of("SHIPPING", "CANCELLED"),
            "SHIPPING", Set.of("COMPLETED"));
    private static final Set<String> EDITABLE = Set.of("PENDING", "CONFIRMED");

    private final JdbcTemplate db;
    private final OrderHistoryController history;

    AdminOrderCommandController(JdbcTemplate db, OrderHistoryController history) {
        this.db = db;
        this.history = history;
    }

    @PostMapping("/{id}/transitions")
    @Transactional
    public ResponseEntity<?> transition(Authentication auth, @PathVariable String id,
            @Valid @RequestBody Transition request) {
        long admin = activeAdmin(auth);
        String target = request.toStatus().strip();
        String reason = request.reason() == null ? "" : request.reason().strip();
        if ("CANCELLED".equals(target) && reason.isEmpty()) {
            throw reject(422, "INVALID_INPUT", "Hủy đơn cần ghi lý do.");
        }
        UUID orderId = orderId(id);
        var order = lock(orderId);
        if (order.version() != request.version()) throw versionConflict();
        if (!TRANSITIONS.getOrDefault(order.status(), Set.of()).contains(target)) {
            throw reject(409, "INVALID_ORDER_TRANSITION", "Không thể chuyển đơn từ " + order.status() + " sang "
                    + target + ".");
        }
        if ("COMPLETED".equals(target) && !Boolean.TRUE.equals(request.codReceived())) {
            throw reject(422, "INVALID_INPUT", "Cần xác nhận đã thu đủ tiền COD để hoàn thành đơn.");
        }

        if ("CANCELLED".equals(target)) {
            var lines = db.query("""
                    SELECT product_id, sku_snapshot, quantity FROM order_item WHERE order_id = ? ORDER BY product_id
                    """, (rs, row) -> new Line(rs.getLong(1), rs.getString(2), rs.getInt(3)), orderId);
            for (var line : lines) {
                db.update("UPDATE product SET available_stock = available_stock + ?, version = version + 1 WHERE id = ?",
                        line.quantity(), line.productId());
                db.update("""
                        INSERT INTO inventory_movement(product_id, sku_snapshot, order_id, actor_id, delta, reason)
                        VALUES (?, ?, ?, ?, ?, 'ORDER_CANCELLED')
                        """, line.productId(), line.sku(), orderId, admin, line.quantity());
            }
        }
        if ("COMPLETED".equals(target)) {
            db.update("""
                    UPDATE customer_order SET status = 'COMPLETED', payment_status = 'PAID', paid_at = now(),
                        version = version + 1 WHERE id = ?
                    """, orderId);
        } else {
            db.update("UPDATE customer_order SET status = ?, version = version + 1 WHERE id = ?", target, orderId);
        }
        db.update("""
                INSERT INTO order_history(order_id, actor_id, action, from_status, to_status, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, orderId, admin, "CANCELLED".equals(target) ? "ORDER_CANCELLED" : "STATUS_CHANGED",
                order.status(), target, reason.isEmpty() ? null : reason);
        return history.findDetail(id, null);
    }

    @PatchMapping("/{id}/items/{productId}")
    @Transactional
    public ResponseEntity<?> changeQuantity(Authentication auth, @PathVariable String id,
            @PathVariable String productId, @Valid @RequestBody QuantityChange request) {
        long admin = activeAdmin(auth);
        int quantity = quantity(request.quantity());
        UUID orderId = orderId(id);
        if (!productId.matches("[1-9][0-9]{0,18}")) throw missingItem();
        long product;
        try {
            product = Long.parseLong(productId);
        } catch (NumberFormatException outOfRange) {
            throw missingItem();
        }
        var order = lock(orderId);
        if (order.version() != request.version()) throw versionConflict();
        if (!EDITABLE.contains(order.status())) {
            throw reject(409, "INVALID_ORDER_TRANSITION", "Chỉ sửa số lượng khi đơn đang chờ hoặc đã xác nhận.");
        }
        var items = db.query("""
                SELECT sku_snapshot, unit_price, quantity FROM order_item WHERE order_id = ? AND product_id = ?
                """, (rs, row) -> new Item(rs.getString(1), rs.getBigDecimal(2), rs.getInt(3)), orderId, product);
        if (items.isEmpty()) throw missingItem();
        var item = items.get(0);
        int delta = quantity - item.quantity();
        if (delta == 0) return history.findDetail(id, null);

        if (delta > 0) reserve(product, delta);
        else db.update("UPDATE product SET available_stock = available_stock + ?, version = version + 1 WHERE id = ?",
                -delta, product);
        db.update("""
                INSERT INTO inventory_movement(product_id, sku_snapshot, order_id, actor_id, delta, reason)
                VALUES (?, ?, ?, ?, ?, 'ORDER_ADJUSTED')
                """, product, item.sku(), orderId, admin, -delta);
        db.update("UPDATE order_item SET quantity = ?, line_total = ? WHERE order_id = ? AND product_id = ?",
                quantity, item.unitPrice().multiply(BigDecimal.valueOf(quantity)), orderId, product);
        db.update("""
                UPDATE customer_order o SET subtotal = s.total, grand_total = s.total + o.shipping_fee,
                    version = o.version + 1
                FROM (SELECT sum(line_total) AS total FROM order_item WHERE order_id = ?) s
                WHERE o.id = ?
                """, orderId, orderId);
        db.update("""
                INSERT INTO order_history(order_id, actor_id, action, from_status, to_status, sku_snapshot,
                    quantity_change, reason)
                VALUES (?, ?, 'QUANTITY_CHANGED', ?, ?, ?, ?, ?)
                """, orderId, admin, order.status(), order.status(), item.sku(), delta, request.reason().strip());
        return history.findDetail(id, null);
    }

    /** Takes extra units from stock. Locks category before SKU, the same order as checkout. */
    private void reserve(long product, int delta) {
        var categories = db.queryForList("SELECT category_id FROM product WHERE id = ?", Long.class, product);
        if (categories.isEmpty()) throw unavailable();
        db.queryForList("SELECT id FROM category WHERE id = ? FOR UPDATE", Long.class, categories.get(0));
        var stock = db.query("""
                SELECT p.available_stock FROM product p JOIN category c ON c.id = p.category_id
                WHERE p.id = ? AND p.category_id = ? AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'
                FOR UPDATE OF p
                """, (rs, row) -> rs.getInt(1), product, categories.get(0));
        if (stock.isEmpty()) throw unavailable();
        if (stock.get(0) < delta) throw reject(422, "INSUFFICIENT_STOCK", "Sản phẩm không đủ tồn kho để tăng số lượng.");
        db.update("UPDATE product SET available_stock = available_stock - ?, version = version + 1 WHERE id = ?",
                delta, product);
    }

    private OrderState lock(UUID orderId) {
        var orders = db.query("SELECT status, version FROM customer_order WHERE id = ? FOR UPDATE",
                (rs, row) -> new OrderState(rs.getString(1), rs.getLong(2)), orderId);
        if (orders.isEmpty()) throw reject(404, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.");
        return orders.get(0);
    }

    private long activeAdmin(Authentication auth) {
        long actor = Long.parseLong(auth.getName());
        boolean admin = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_account WHERE id = ? AND status = 'ACTIVE' AND role = 'ADMIN')
                """, Boolean.class, actor));
        if (!admin) throw reject(403, "ADMIN_REQUIRED", "Yêu cầu tài khoản quản trị đang hoạt động.");
        return actor;
    }

    private static UUID orderId(String id) {
        if (!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw reject(404, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.");
        }
        return UUID.fromString(id);
    }

    private static int quantity(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < 1 || value.intValue() > 99) {
            throw reject(422, "INVALID_QUANTITY", "Số lượng phải là số nguyên từ 1 đến 99. Muốn bỏ cả đơn hãy hủy đơn.");
        }
        return value.intValue();
    }

    private static OrderPlacement.Rejected versionConflict() {
        return reject(409, "VERSION_CONFLICT", "Đơn hàng đã thay đổi. Hãy tải lại đơn.");
    }

    private static OrderPlacement.Rejected missingItem() {
        return reject(404, "ORDER_ITEM_NOT_FOUND", "Dòng hàng không thuộc đơn này.");
    }

    private static OrderPlacement.Rejected unavailable() {
        return reject(422, "PRODUCT_UNAVAILABLE", "Sản phẩm đã ngừng bán nên không thể tăng số lượng.");
    }

    private static OrderPlacement.Rejected reject(int status, String code, String message) {
        return new OrderPlacement.Rejected(status, code, message);
    }

    @ExceptionHandler(OrderPlacement.Rejected.class)
    ResponseEntity<?> rejected(OrderPlacement.Rejected error) {
        return CheckoutPreviewController.error(error.status, error.code, error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid() {
        return CheckoutPreviewController.error(422, "INVALID_INPUT", "Thông tin cập nhật đơn không hợp lệ.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() {
        return CheckoutPreviewController.error(400, "INVALID_REQUEST", "Nội dung yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> dependency() {
        return CheckoutPreviewController.error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể cập nhật đơn.");
    }

    record Transition(@NotBlank @Size(max = 20) String toStatus, @Size(max = 500) String reason,
            @NotNull @PositiveOrZero Long version, Boolean codReceived) {}
    record QuantityChange(JsonNode quantity, @NotBlank @Size(max = 500) String reason,
            @NotNull @PositiveOrZero Long version) {}
    private record OrderState(String status, long version) {}
    private record Line(long productId, String sku, int quantity) {}
    private record Item(String sku, BigDecimal unitPrice, int quantity) {}
}
