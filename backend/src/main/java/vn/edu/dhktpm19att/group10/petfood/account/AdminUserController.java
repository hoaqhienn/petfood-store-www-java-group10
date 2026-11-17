package vn.edu.dhktpm19att.group10.petfood.account;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Admin user management (F08, BR08-BR09). Email and role stay read-only in v1. */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "LOCKED");
    private static final Set<String> ROLES = Set.of("CUSTOMER", "ADMIN");
    private static final Set<String> WRITABLE = Set.of("displayName", "phone", "status", "version");
    private static final String FIELDS = """
            u.id, u.email_normalized, u.display_name, u.phone, u.role, u.status, u.created_at, u.version,
            (SELECT count(*) FROM customer_order o WHERE o.customer_id = u.id) AS order_count
            """;
    private static final RowMapper<User> USER = (rs, row) -> new User(rs.getString("id"),
            rs.getString("email_normalized"), rs.getString("display_name"), rs.getString("phone"),
            rs.getString("role"), rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getLong("version"),
            rs.getLong("order_count"));

    private final JdbcTemplate db;

    AdminUserController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> list(Authentication auth, @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        activeAdmin(auth);
        // An empty filter from a form (status=) means "no filter", not an invalid value.
        if (status != null && status.isBlank()) status = null;
        if (role != null && role.isBlank()) role = null;
        if (page < 0 || size < 1 || size > 100 || q.length() > 254
                || (status != null && !STATUSES.contains(status)) || (role != null && !ROLES.contains(role))) {
            throw new Rejected(400, "INVALID_QUERY", "Bộ lọc hoặc phân trang không hợp lệ.");
        }
        var where = new StringBuilder(" FROM user_account u WHERE TRUE");
        var args = new ArrayList<Object>();
        if (!q.isBlank()) {
            where.append(" AND (strpos(u.email_normalized, lower(?)) > 0 OR strpos(lower(u.display_name), lower(?)) > 0)");
            args.add(q.strip());
            args.add(q.strip());
        }
        if (status != null) {
            where.append(" AND u.status = ?");
            args.add(status);
        }
        if (role != null) {
            where.append(" AND u.role = ?");
            args.add(role);
        }
        long total = db.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var items = db.query("SELECT " + FIELDS + where + " ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?",
                USER, args.toArray());
        return ResponseEntity.ok(new Page(items, page, size, total, total / size + (total % size == 0 ? 0 : 1)));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> detail(Authentication auth, @PathVariable String id) {
        activeAdmin(auth);
        return ResponseEntity.ok(find(userId(id)));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(Authentication auth, @PathVariable String id, @RequestBody JsonNode body) {
        long admin = activeAdmin(auth);
        long target = userId(id);
        if (body == null || !body.isObject()) throw invalid("Nội dung cập nhật phải là object JSON.");
        var fields = new ArrayList<String>();
        body.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            // Whitelist: email, role, password or any other field cannot be mass-assigned (AC33).
            if (!WRITABLE.contains(field)) throw invalid("Không được cập nhật trường " + field + ".");
        }
        JsonNode version = body.get("version");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0) {
            throw invalid("Thiếu version hợp lệ.");
        }

        var current = lockUser(target);
        if (current.version() != version.longValue()) {
            throw new Rejected(409, "VERSION_CONFLICT", "Tài khoản đã thay đổi. Hãy tải lại.");
        }
        String displayName = current.displayName();
        if (body.has("displayName")) {
            JsonNode value = body.get("displayName");
            if (!value.isTextual() || value.asText().isBlank() || value.asText().strip().length() > 120) {
                throw invalid("Tên hiển thị phải có từ 1 đến 120 ký tự.");
            }
            displayName = value.asText().strip();
        }
        String phone = current.phone();
        if (body.has("phone")) {
            JsonNode value = body.get("phone");
            if (value.isNull() || (value.isTextual() && value.asText().isBlank())) {
                phone = null;
            } else if (value.isTextual() && value.asText().strip().matches("[+0-9][0-9 ()-]{7,19}")) {
                phone = value.asText().strip();
            } else {
                throw invalid("Số điện thoại không hợp lệ.");
            }
        }
        String status = current.status();
        if (body.has("status")) {
            JsonNode value = body.get("status");
            if (!value.isTextual() || !STATUSES.contains(value.asText())) throw invalid("Trạng thái không hợp lệ.");
            status = value.asText();
        }
        if ("LOCKED".equals(status) && !"LOCKED".equals(current.status()) && "ADMIN".equals(current.role())) {
            protectAdmin(admin, target);
        }

        db.update("""
                UPDATE user_account SET display_name = ?, phone = ?, status = ?, version = version + 1 WHERE id = ?
                """, displayName, phone, status, target);
        return ResponseEntity.ok(find(target));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(Authentication auth, @PathVariable String id) {
        long admin = activeAdmin(auth);
        long target = userId(id);
        var current = lockUser(target);
        if ("ADMIN".equals(current.role())) protectAdmin(admin, target);
        // The account row is locked, so a checkout for this user cannot commit between this check and the delete.
        long orders = db.queryForObject("SELECT count(*) FROM customer_order WHERE customer_id = ?", Long.class, target);
        if (orders > 0) {
            throw new Rejected(409, "USER_HAS_ORDERS", "Tài khoản đã có đơn hàng (kể cả đơn hủy) nên chỉ có thể khóa.");
        }
        db.update("""
                UPDATE notification_outbox SET status = 'CANCELLED'
                WHERE aggregate_id = ? AND status = 'PENDING' AND type IN ('REGISTRATION', 'ORDER_PLACED')
                """, target);
        db.update("DELETE FROM user_account WHERE id = ?", target);
        return ResponseEntity.noContent().build();
    }

    /** Never lock or delete yourself, nor leave the store without an ACTIVE admin. */
    private void protectAdmin(long admin, long target) {
        if (admin == target) {
            throw new Rejected(409, "ADMIN_PROTECTED", "Không thể khóa hoặc xóa chính tài khoản đang dùng.");
        }
        var activeAdmins = db.queryForList("""
                SELECT id FROM user_account WHERE role = 'ADMIN' AND status = 'ACTIVE' ORDER BY id FOR UPDATE
                """, Long.class);
        if (activeAdmins.stream().noneMatch(other -> other != target)) {
            throw new Rejected(409, "ADMIN_PROTECTED", "Phải còn ít nhất một quản trị viên đang hoạt động.");
        }
    }

    private Locked lockUser(long id) {
        var users = db.query("SELECT display_name, phone, role, status, version FROM user_account WHERE id = ? FOR UPDATE",
                (rs, row) -> new Locked(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5)), id);
        if (users.isEmpty()) throw missing();
        return users.get(0);
    }

    private User find(long id) {
        var users = db.query("SELECT " + FIELDS + " FROM user_account u WHERE u.id = ?", USER, id);
        if (users.isEmpty()) throw missing();
        return users.get(0);
    }

    private long activeAdmin(Authentication auth) {
        long actor = Long.parseLong(auth.getName());
        boolean admin = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_account WHERE id = ? AND status = 'ACTIVE' AND role = 'ADMIN')
                """, Boolean.class, actor));
        if (!admin) throw new Rejected(403, "ADMIN_REQUIRED", "Yêu cầu tài khoản quản trị đang hoạt động.");
        return actor;
    }

    private static long userId(String id) {
        if (!id.matches("[1-9][0-9]{0,18}")) throw missing();
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException outOfRange) {
            throw missing();
        }
    }

    private static Rejected missing() {
        return new Rejected(404, "USER_NOT_FOUND", "Không tìm thấy tài khoản.");
    }

    private static Rejected invalid(String message) {
        return new Rejected(422, "INVALID_INPUT", message);
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<?> rejected(Rejected error) {
        return error(error.status, error.code, error.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed() {
        return error(400, "INVALID_REQUEST", "Yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể xử lý tài khoản.");
    }

    private static ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message,
                "fieldErrors", List.of(), "traceId", UUID.randomUUID().toString()));
    }

    record User(String id, String email, String displayName, String phone, String role, String status,
            Instant createdAt, long version, long orderCount) {}
    record Page(List<User> items, int page, int size, long totalElements, long totalPages) {}
    private record Locked(String displayName, String phone, String role, String status, long version) {}

    static class Rejected extends RuntimeException {
        final int status;
        final String code;

        Rejected(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
