package vn.edu.dhktpm19att.group10.petfood.tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Dashboard numbers with their definitions (BR26). Behaviour figures only cover visitors who consented to
 * analytics; revenue comes from orders and counts COMPLETED, PAID COD orders only (BR18).
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
class AdminAnalyticsController {
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> FUNNEL = List.of("product_view", "add_to_cart", "checkout_start", "order_placed");
    private static final Map<String, String> DEFINITIONS = Map.of(
            "scope", "Chỉ gồm khách đã đồng ý analytics; khách từ chối không xuất hiện trong số liệu hành vi.",
            "funnel", "Số phiên (token ẩn danh theo Session) đi qua từng bước theo đúng thứ tự thời gian máy chủ"
                    + " nhận; mỗi phiên đếm tối đa một lần mỗi bước.",
            "ctr", "Số impressionId có ít nhất một lượt click chia cho số impressionId đã hiển thị; click lặp không"
                    + " đếm thêm.",
            "revenue", "Tổng grandTotal của đơn COMPLETED đã thu COD (PAID), theo thời điểm thu tiền.",
            "window", "Từ 00:00 ngày bắt đầu đến hết ngày kết thúc theo giờ Việt Nam.");

    private final JdbcTemplate db;

    AdminAnalyticsController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> analytics(Authentication auth, @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo, @RequestParam(defaultValue = "false") boolean includeDemo) {
        boolean admin = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_account WHERE id = ? AND status = 'ACTIVE' AND role = 'ADMIN')
                """, Boolean.class, Long.parseLong(auth.getName())));
        if (!admin) return TrackingController.error(403, "ADMIN_REQUIRED", "Yêu cầu tài khoản quản trị đang hoạt động.");
        LocalDate to = dateTo == null ? LocalDate.now(STORE_ZONE) : dateTo;
        LocalDate from = dateFrom == null ? to.minusDays(29) : dateFrom;
        if (from.isAfter(to) || from.plusDays(366).isBefore(to)) {
            return TrackingController.error(400, "INVALID_QUERY", "Khoảng ngày không hợp lệ (tối đa 366 ngày).");
        }
        OffsetDateTime start = from.atStartOfDay(STORE_ZONE).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(STORE_ZONE).toOffsetDateTime();

        String events = """
                SELECT anon_token, name, server_received_at AS t FROM behavior_event
                WHERE server_received_at >= ? AND server_received_at < ? AND (? OR NOT is_demo)
                """;
        var funnel = db.queryForObject("""
                WITH ev AS (%s),
                s1 AS (SELECT anon_token, min(t) AS t FROM ev WHERE name = 'product_view' GROUP BY anon_token),
                s2 AS (SELECT s.anon_token, min(e.t) AS t FROM s1 s JOIN ev e ON e.anon_token = s.anon_token
                       AND e.name = 'add_to_cart' AND e.t >= s.t GROUP BY s.anon_token),
                s3 AS (SELECT s.anon_token, min(e.t) AS t FROM s2 s JOIN ev e ON e.anon_token = s.anon_token
                       AND e.name = 'checkout_start' AND e.t >= s.t GROUP BY s.anon_token),
                s4 AS (SELECT s.anon_token, min(e.t) AS t FROM s3 s JOIN ev e ON e.anon_token = s.anon_token
                       AND e.name = 'order_placed' AND e.t >= s.t GROUP BY s.anon_token)
                SELECT (SELECT count(*) FROM s1), (SELECT count(*) FROM s2), (SELECT count(*) FROM s3),
                       (SELECT count(*) FROM s4)
                """.formatted(events), (rs, row) -> List.of(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                start, end, includeDemo);
        var steps = new LinkedHashMap<String, Long>();
        for (int i = 0; i < FUNNEL.size(); i++) steps.put(FUNNEL.get(i), funnel.get(i));

        var ctr = db.query("""
                SELECT i.personalized, count(*) AS shown,
                       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM behavior_event e
                           WHERE e.impression_id = i.impression_id AND e.name = 'recommendation_click')) AS clicked
                FROM recommendation_impression i
                WHERE i.created_at >= ? AND i.created_at < ? AND (? OR NOT i.is_demo)
                GROUP BY i.personalized
                """, (rs, row) -> new Ctr(rs.getBoolean(1) ? "personalized" : "popular", rs.getLong(2), rs.getLong(3),
                rate(rs.getLong(3), rs.getLong(2))), start, end, includeDemo);
        long shown = ctr.stream().mapToLong(Ctr::impressions).sum();
        long clicked = ctr.stream().mapToLong(Ctr::clicked).sum();

        long visitors = db.queryForObject("""
                SELECT count(DISTINCT subject_key) FROM behavior_event
                WHERE server_received_at >= ? AND server_received_at < ? AND (? OR NOT is_demo)
                """, Long.class, start, end, includeDemo);
        long demoEvents = db.queryForObject("""
                SELECT count(*) FROM behavior_event WHERE server_received_at >= ? AND server_received_at < ? AND is_demo
                """, Long.class, start, end);
        var consent = db.queryForObject("""
                SELECT count(*) FILTER (WHERE analytics), count(*) FILTER (WHERE personalization) FROM consent_preference
                """, (rs, row) -> Map.of("analytics", rs.getLong(1), "personalization", rs.getLong(2)));
        BigDecimal revenue = db.queryForObject("""
                SELECT coalesce(sum(grand_total), 0) FROM customer_order
                WHERE status = 'COMPLETED' AND payment_status = 'PAID' AND paid_at >= ? AND paid_at < ?
                """, BigDecimal.class, start, end);
        var topViewed = db.query("""
                SELECT p.id, p.name, count(*) AS views FROM behavior_event e JOIN product p ON p.id = e.product_id
                WHERE e.name = 'product_view' AND e.server_received_at >= ? AND e.server_received_at < ?
                  AND (? OR NOT e.is_demo)
                GROUP BY p.id, p.name ORDER BY views DESC, p.id LIMIT 5
                """, (rs, row) -> new Viewed(rs.getString(1), rs.getString(2), rs.getLong(3)), start, end, includeDemo);

        return ResponseEntity.ok(new Report(new Window(from, to, STORE_ZONE.getId()), includeDemo, demoEvents,
                visitors, consent, steps, new CtrSummary(shown, clicked, rate(clicked, shown), ctr),
                revenue.toPlainString(), topViewed, DEFINITIONS));
    }

    private static String rate(long part, long whole) {
        if (whole == 0) return null;
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP).toPlainString();
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, java.time.DateTimeException.class})
    ResponseEntity<?> invalid() {
        return TrackingController.error(400, "INVALID_QUERY", "Khoảng ngày không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return TrackingController.error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể tính số liệu.");
    }

    record Window(LocalDate dateFrom, LocalDate dateTo, String timezone) {}
    record Ctr(String group, long impressions, long clicked, String ctr) {}
    record CtrSummary(long impressions, long clicked, String ctr, List<Ctr> byGroup) {}
    record Viewed(String productId, String name, long views) {}
    record Report(Window window, boolean demoIncluded, long demoEventsInWindow, long consentingVisitors,
            Map<String, Long> consentCounts, Map<String, Long> funnel, CtrSummary ctr, String completedRevenue,
            List<Viewed> topViewedProducts, Map<String, String> definitions) {}
}
