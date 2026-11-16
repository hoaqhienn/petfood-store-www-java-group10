package vn.edu.dhktpm19att.group10.petfood.account;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Admin view of the email outbox (BR19-BR20). Retrying re-sends mail only; it never recreates a user or order. */
@RestController
@RequestMapping("/api/v1/admin/notifications")
class AdminNotificationController {
    private static final Set<String> STATUSES = Set.of("PENDING", "SENT", "FAILED", "CANCELLED");
    private static final Set<String> TYPES = Set.of("REGISTRATION", "ORDER_PLACED");
    private static final String SELECT = """
            SELECT o.id, o.type, o.status, o.attempts, o.next_attempt_at, o.created_at, o.sent_at,
                   o.payload->>'orderCode' AS order_code, a.email_normalized
            FROM notification_outbox o LEFT JOIN user_account a ON a.id = o.aggregate_id
            """;
    private static final RowMapper<Notification> NOTIFICATION = (rs, row) -> new Notification(
            rs.getString("id"), rs.getString("type"), rs.getString("status"), rs.getInt("attempts"),
            instant(rs.getObject("next_attempt_at", OffsetDateTime.class)),
            instant(rs.getObject("created_at", OffsetDateTime.class)),
            instant(rs.getObject("sent_at", OffsetDateTime.class)),
            rs.getString("order_code"), mask(rs.getString("email_normalized")));

    private final JdbcTemplate db;

    AdminNotificationController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> list(Authentication auth, @RequestParam(required = false) String status,
            @RequestParam(required = false) String type, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!activeAdmin(auth)) return forbidden();
        if (page < 0 || size < 1 || size > 100 || (status != null && !STATUSES.contains(status))
                || (type != null && !TYPES.contains(type))) {
            return error(400, "INVALID_QUERY", "Bộ lọc hoặc phân trang không hợp lệ.");
        }
        var where = new StringBuilder(" WHERE TRUE");
        var args = new ArrayList<Object>();
        if (status != null) {
            where.append(" AND o.status = ?");
            args.add(status);
        }
        if (type != null) {
            where.append(" AND o.type = ?");
            args.add(type);
        }
        long total = db.queryForObject("SELECT count(*) FROM notification_outbox o" + where, Long.class,
                args.toArray());
        args.add(size);
        args.add((long) page * size);
        var items = db.query(SELECT + where + " ORDER BY o.created_at DESC, o.id LIMIT ? OFFSET ?", NOTIFICATION,
                args.toArray());
        return ResponseEntity.ok(new Page(items, page, size, total, total / size + (total % size == 0 ? 0 : 1)));
    }

    @PostMapping("/{id}/retry")
    @Transactional
    public ResponseEntity<?> retry(Authentication auth, @PathVariable String id) {
        if (!activeAdmin(auth)) return forbidden();
        if (!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return missing();
        UUID notification = UUID.fromString(id);
        var statuses = db.queryForList("SELECT status FROM notification_outbox WHERE id = ? FOR UPDATE",
                String.class, notification);
        if (statuses.isEmpty()) return missing();
        if (!"FAILED".equals(statuses.get(0))) {
            return error(409, "NOTIFICATION_NOT_FAILED", "Chỉ gửi lại được email đã thất bại.");
        }
        // The worker picks it up again with a fresh retry budget.
        db.update("""
                UPDATE notification_outbox SET status = 'PENDING', attempts = 0, next_attempt_at = now() WHERE id = ?
                """, notification);
        return ResponseEntity.ok(db.query(SELECT + " WHERE o.id = ?", NOTIFICATION, notification).get(0));
    }

    private boolean activeAdmin(Authentication auth) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_account WHERE id = ? AND status = 'ACTIVE' AND role = 'ADMIN')
                """, Boolean.class, Long.parseLong(auth.getName())));
    }

    /** Shows enough to recognise the recipient without exposing the full address. */
    static String mask(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static ResponseEntity<?> forbidden() {
        return error(403, "ADMIN_REQUIRED", "Yêu cầu tài khoản quản trị đang hoạt động.");
    }

    private static ResponseEntity<?> missing() {
        return error(404, "NOTIFICATION_NOT_FOUND", "Không tìm thấy email.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<?> invalidQuery() {
        return error(400, "INVALID_QUERY", "Bộ lọc hoặc phân trang không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể xem email.");
    }

    private static ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message,
                "fieldErrors", List.of(), "traceId", UUID.randomUUID().toString()));
    }

    record Notification(String id, String type, String status, int attempts, Instant nextAttemptAt,
            Instant createdAt, Instant sentAt, String orderCode, String recipient) {}
    record Page(List<Notification> items, int page, int size, long totalElements, long totalPages) {}
}
