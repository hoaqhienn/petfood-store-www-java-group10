package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/admin/orders")
class AdminOrderController {
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> STATUSES = Set.of("PENDING", "CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED");

    private final JdbcTemplate db;
    private final OrderHistoryController history;

    AdminOrderController(JdbcTemplate db, OrderHistoryController history) {
        this.db = db;
        this.history = history;
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> list(Authentication auth, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String customer, @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo) {
        if (!activeAdmin(auth)) return forbidden();
        if (page < 0 || size < 1 || size > 100 || (orderCode != null && orderCode.length() > 100)
                || (customer != null && customer.length() > 254) || (status != null && !STATUSES.contains(status))
                || (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo))) return invalid();
        var sql = new StringBuilder(" FROM customer_order o JOIN user_account u ON u.id = o.customer_id WHERE TRUE");
        var args = new ArrayList<Object>();
        if (orderCode != null && !orderCode.isBlank()) {
            sql.append(" AND strpos(lower(o.order_code), lower(?)) > 0");
            args.add(orderCode.strip());
        }
        if (customer != null && !customer.isBlank()) {
            sql.append(" AND (strpos(lower(u.email_normalized), lower(?)) > 0"
                    + " OR strpos(lower(u.display_name), lower(?)) > 0)");
            args.add(customer.strip());
            args.add(customer.strip());
        }
        if (status != null) {
            sql.append(" AND o.status = ?");
            args.add(status);
        }
        if (dateFrom != null) {
            sql.append(" AND o.created_at >= ?");
            args.add(dateFrom.atStartOfDay(STORE_ZONE).toOffsetDateTime());
        }
        if (dateTo != null) {
            sql.append(" AND o.created_at < ?");
            args.add(dateTo.plusDays(1).atStartOfDay(STORE_ZONE).toOffsetDateTime());
        }
        long total = db.queryForObject("SELECT count(*)" + sql, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var items = db.query("""
                SELECT o.*, u.display_name AS customer_name, u.email_normalized AS customer_email
                """ + sql + " ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?", (rs, row) ->
                new Entry(OrderHistoryController.summary(rs), rs.getString("customer_id"),
                        rs.getString("customer_name"), rs.getString("customer_email")), args.toArray());
        return ResponseEntity.ok(new Page(items, page, size, total, total / size + (total % size == 0 ? 0 : 1)));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> detail(Authentication auth, @PathVariable String id) {
        if (!activeAdmin(auth)) return forbidden();
        return history.findDetail(id, null);
    }

    private boolean activeAdmin(Authentication auth) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_account WHERE id = ? AND status = 'ACTIVE' AND role = 'ADMIN')
                """, Boolean.class, Long.parseLong(auth.getName())));
    }

    private static ResponseEntity<?> forbidden() {
        return CheckoutPreviewController.error(403, "ADMIN_REQUIRED", "Yêu cầu tài khoản quản trị đang hoạt động.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, java.time.DateTimeException.class})
    ResponseEntity<?> invalid() {
        return CheckoutPreviewController.error(400, "INVALID_QUERY", "Bộ lọc hoặc phân trang không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return CheckoutPreviewController.error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể xem đơn hàng.");
    }

    record Page(java.util.List<Entry> items, int page, int size, long totalElements, long totalPages) {}
    record Entry(OrderHistoryController.Summary order, String customerId, String customerName, String customerEmail) {}
}
