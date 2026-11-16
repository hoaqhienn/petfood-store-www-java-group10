package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
class OrderCancellationController {
    private final JdbcTemplate db;

    OrderCancellationController(JdbcTemplate db) {
        this.db = db;
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancel(Authentication authentication, @PathVariable String id,
            @Valid @RequestBody CancelRequest request) {
        long actor = Long.parseLong(authentication.getName());
        var roles = db.query("""
                SELECT role FROM user_account WHERE id = ? AND status = 'ACTIVE' FOR UPDATE
                """, (rs, row) -> rs.getString(1), actor);
        if (roles.isEmpty() || !List.of("CUSTOMER", "ADMIN").contains(roles.get(0))) {
            return CheckoutPreviewController.error(403, "ACCOUNT_LOCKED", "Tài khoản không thể hủy đơn.");
        }
        UUID orderId;
        try {
            orderId = UUID.fromString(id);
            if (!orderId.toString().equalsIgnoreCase(id)) return missing();
        } catch (IllegalArgumentException exception) {
            return missing();
        }
        var orders = db.query("""
                SELECT status, version FROM customer_order WHERE id = ? AND customer_id = ? FOR UPDATE
                """, (rs, row) -> new OrderState(rs.getString(1), rs.getLong(2)), orderId, actor);
        if (orders.isEmpty()) return missing();
        var order = orders.get(0);
        if (order.version() != request.version()) {
            return CheckoutPreviewController.error(409, "VERSION_CONFLICT", "Đơn hàng đã thay đổi.");
        }
        if (!"PENDING".equals(order.status())) {
            return CheckoutPreviewController.error(409, "INVALID_ORDER_TRANSITION", "Chỉ có thể hủy đơn đang chờ.");
        }
        var lines = db.query("""
                SELECT product_id, sku_snapshot, quantity FROM order_item
                WHERE order_id = ? ORDER BY product_id
                """, (rs, row) -> new Line(rs.getLong(1), rs.getString(2), rs.getInt(3)), orderId);
        for (var line : lines) {
            db.update("UPDATE product SET available_stock = available_stock + ?, version = version + 1 WHERE id = ?",
                    line.quantity(), line.productId());
            db.update("""
                    INSERT INTO inventory_movement(product_id, sku_snapshot, order_id, actor_id, delta, reason)
                    VALUES (?, ?, ?, ?, ?, 'ORDER_CANCELLED')
                    """, line.productId(), line.sku(), orderId, actor, line.quantity());
        }
        db.update("UPDATE customer_order SET status = 'CANCELLED', version = version + 1 WHERE id = ?", orderId);
        db.update("""
                INSERT INTO order_history(order_id, actor_id, action, to_status, reason)
                VALUES (?, ?, 'ORDER_CANCELLED', 'CANCELLED', ?)
                """, orderId, actor, request.reason().strip());
        return ResponseEntity.ok(new Cancellation(orderId, "CANCELLED", order.version() + 1));
    }

    private static ResponseEntity<?> missing() {
        return CheckoutPreviewController.error(404, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return CheckoutPreviewController.error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể hủy đơn.");
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid() {
        return CheckoutPreviewController.error(422, "INVALID_INPUT", "Thông tin hủy đơn không hợp lệ.");
    }

    record CancelRequest(@NotBlank @Size(max = 500) String reason, @NotNull @PositiveOrZero Long version) {}
    record Cancellation(UUID id, String status, long version) {}
    private record OrderState(String status, long version) {}
    private record Line(long productId, String sku, int quantity) {}
}
