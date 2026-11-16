package vn.edu.dhktpm19att.group10.petfood.tracking;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Consent, behaviour events and recommendations (F14-F15, BR24-BR26). */
@RestController
@RequestMapping("/api/v1")
class TrackingController {
    /** Events a browser may send. order_placed and recommendation_impression come only from the backend. */
    private static final Set<String> CLIENT_EVENTS = Set.of("page_view", "product_view", "search", "add_to_cart",
            "checkout_start", "recommendation_click");
    private static final Set<String> PRODUCT_EVENTS = Set.of("product_view", "add_to_cart");
    private static final Set<String> SPECIES = Set.of("DOG", "CAT");
    private static final Set<String> LIFE_STAGES = Set.of("JUNIOR", "ADULT", "SENIOR");
    private static final int MAX_BATCH = 20;

    private final Tracking tracking;
    private final JdbcTemplate db;

    TrackingController(Tracking tracking, JdbcTemplate db) {
        this.tracking = tracking;
        this.db = db;
    }

    @GetMapping("/consent")
    Tracking.Consent consent(HttpSession session, Authentication auth) {
        return tracking.consent(Tracking.subject(session, auth));
    }

    @PutMapping("/consent")
    @Transactional
    public Tracking.Consent updateConsent(@RequestBody JsonNode body, HttpSession session, Authentication auth) {
        if (body == null || !body.isObject() || !body.path("analytics").isBoolean()
                || !body.path("personalization").isBoolean() || body.size() != 2) {
            throw new Rejected(422, "INVALID_INPUT", "Cần đúng hai lựa chọn analytics và personalization (true/false).");
        }
        var subject = Tracking.subject(session, auth);
        boolean analytics = body.get("analytics").asBoolean();
        boolean personalization = body.get("personalization").asBoolean();
        db.update("""
                INSERT INTO consent_preference (subject_key, user_id, analytics, personalization, policy_version)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (subject_key) DO UPDATE SET analytics = EXCLUDED.analytics,
                    personalization = EXCLUDED.personalization, policy_version = EXCLUDED.policy_version,
                    updated_at = now()
                """, subject.key(), subject.userId(), analytics, personalization, Tracking.POLICY_VERSION);
        // Withdrawing consent stops new writes and removes what was collected under it (BR26). Orders stay.
        if (!analytics) {
            db.update("DELETE FROM behavior_event WHERE subject_key = ?", subject.key());
            db.update("DELETE FROM recommendation_impression WHERE subject_key = ?", subject.key());
        }
        return tracking.consent(subject);
    }

    @PostMapping("/events")
    @Transactional
    public Map<String, Integer> events(@RequestBody JsonNode body, HttpSession session, Authentication auth) {
        JsonNode events = body == null ? null : body.get("events");
        if (events == null || !events.isArray() || events.isEmpty() || events.size() > MAX_BATCH) {
            throw new Rejected(422, "INVALID_INPUT", "Gửi từ 1 đến " + MAX_BATCH + " sự kiện mỗi lần.");
        }
        var subject = Tracking.subject(session, auth);
        if (!tracking.consent(subject).analytics()) {
            // No consent: accept the request so the page keeps working, but store nothing (AC27).
            return Map.of("stored", 0, "duplicates", 0, "rejected", 0, "ignored", events.size());
        }
        int stored = 0, duplicates = 0, rejected = 0;
        for (JsonNode event : events) {
            var parsed = parse(event, subject);
            if (parsed == null) {
                rejected++;
                continue;
            }
            int inserted = tracking.insert(subject, parsed.eventId(), parsed.name(), parsed.productId(),
                    parsed.impressionId(), parsed.metadata(), parsed.clientTime());
            if (inserted == 1) stored++;
            else duplicates++;
        }
        return Map.of("stored", stored, "duplicates", duplicates, "rejected", rejected, "ignored", 0);
    }

    /** Validates one event against the whitelist; returns null when it must be dropped. */
    private Parsed parse(JsonNode event, Tracking.Subject subject) {
        if (!event.isObject()) return null;
        String name = event.path("name").asText("");
        if (!CLIENT_EVENTS.contains(name)) return null;
        UUID eventId;
        try {
            eventId = UUID.fromString(event.path("eventId").asText(""));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        Instant clientTime = null;
        if (event.hasNonNull("clientTime")) {
            try {
                clientTime = OffsetDateTime.parse(event.get("clientTime").asText()).toInstant();
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
        Long productId = null;
        UUID impressionId = null;
        if (PRODUCT_EVENTS.contains(name)) {
            String id = event.path("productId").asText("");
            if (!id.matches("[1-9][0-9]{0,17}")) return null;
            productId = Long.valueOf(id);
            if (db.queryForObject("SELECT count(*) FROM product WHERE id = ?", Integer.class, productId) == 0) {
                return null;
            }
        }
        if ("recommendation_click".equals(name)) {
            // A click only counts for an impression the backend issued to this same visitor (BR26).
            try {
                impressionId = UUID.fromString(event.path("impressionId").asText(""));
            } catch (IllegalArgumentException invalid) {
                return null;
            }
            var products = db.query("""
                    SELECT product_id FROM recommendation_impression WHERE impression_id = ? AND subject_key = ?
                    """, (rs, row) -> (Long) rs.getObject(1), impressionId, subject.key());
            if (products.isEmpty()) return null;
            productId = products.get(0);
        }
        var metadata = new HashMap<String, Object>();
        JsonNode raw = event.path("metadata");
        if ("search".equals(name)) {
            // Only normalised filters and sizes: raw search text could contain personal data (BR25).
            if (SPECIES.contains(raw.path("species").asText())) metadata.put("species", raw.get("species").asText());
            if (LIFE_STAGES.contains(raw.path("lifeStage").asText())) {
                metadata.put("lifeStage", raw.get("lifeStage").asText());
            }
            if (raw.path("resultCount").canConvertToInt() && raw.path("resultCount").isIntegralNumber()) {
                metadata.put("resultCount", Math.max(0, Math.min(1000, raw.get("resultCount").intValue())));
            }
            if (raw.path("queryLength").isIntegralNumber()) {
                metadata.put("queryLength", Math.max(0, Math.min(300, raw.get("queryLength").intValue())));
            }
        }
        if ("page_view".equals(name) && raw.path("path").isTextual()) {
            String path = raw.get("path").asText();
            // Keep only the route, never query strings that might carry personal data.
            if (path.matches("/[A-Za-z0-9/_-]{0,100}")) metadata.put("path", path);
        }
        return new Parsed(eventId, name, productId, impressionId, metadata, clientTime);
    }

    @GetMapping("/recommendations")
    @Transactional
    public Recommendations recommendations(@RequestParam(defaultValue = "4") int limit, HttpSession session,
            Authentication auth) {
        if (limit < 1 || limit > 12) throw new Rejected(400, "INVALID_QUERY", "limit phải từ 1 đến 12.");
        var subject = Tracking.subject(session, auth);
        var consent = tracking.consent(subject);
        Profile profile = consent.personalization() ? profile(subject) : null;
        List<Pick> picks = new ArrayList<>();
        boolean personalized = false;
        if (profile != null) {
            picks = db.query("""
                    SELECT p.id, p.name, p.price, p.available_stock FROM product p JOIN category c ON c.id = p.category_id
                    WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE' AND p.available_stock > 0
                      AND p.species IN (?, 'BOTH') AND (CAST(? AS text) IS NULL OR p.life_stage IN (?, 'ALL'))
                    ORDER BY (SELECT count(*) FROM order_item i WHERE i.product_id = p.id) DESC, p.name, p.id
                    LIMIT ?
                    """, (rs, row) -> new Pick(rs.getString(1), rs.getString(2), rs.getBigDecimal(3).toPlainString(),
                    rs.getInt(4)), profile.species(), profile.lifeStage(), profile.lifeStage(), limit);
            personalized = !picks.isEmpty();
        }
        if (picks.isEmpty()) picks = popular(limit);

        UUID recommendationId = UUID.randomUUID();
        String reason = personalized ? profile.reason() : "Được nhiều khách mua gần đây.";
        var items = new ArrayList<Item>();
        for (int i = 0; i < picks.size(); i++) {
            var pick = picks.get(i);
            UUID impression = UUID.randomUUID();
            // Impressions are stored only with analytics consent, so CTR covers the consenting visitors only.
            if (consent.analytics()) {
                db.update("""
                        INSERT INTO recommendation_impression (impression_id, recommendation_id, subject_key,
                            anon_token, user_id, product_id, position, personalized)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, impression, recommendationId, subject.key(), subject.anonToken(), subject.userId(),
                        Long.valueOf(pick.id()), i, personalized);
            }
            items.add(new Item(impression, pick.id(), pick.name(), pick.price(), pick.availableStock(),
                    "/products/" + pick.id(), reason));
        }
        return new Recommendations(recommendationId, personalized, items);
    }

    /**
     * What this visitor's own history says: the species and life stage they viewed most in 90 days, or else the
     * species of their own pet profiles. Never uses another visitor's data (AC29).
     */
    private Profile profile(Tracking.Subject subject) {
        var viewed = db.query("""
                SELECT p.species, p.life_stage, count(*) AS views
                FROM behavior_event e JOIN product p ON p.id = e.product_id
                WHERE e.subject_key = ? AND e.name IN ('product_view', 'add_to_cart')
                  AND e.server_received_at > now() - interval '90 days' AND p.species IN ('DOG', 'CAT')
                GROUP BY p.species, p.life_stage ORDER BY views DESC, p.species LIMIT 1
                """, (rs, row) -> new Profile(rs.getString(1),
                LIFE_STAGES.contains(rs.getString(2)) ? rs.getString(2) : null,
                "Gợi ý theo các sản phẩm bạn đã xem gần đây."), subject.key());
        if (!viewed.isEmpty()) return viewed.get(0);
        if (subject.userId() == null) return null;
        var pets = db.queryForList("""
                SELECT species FROM pet_profile WHERE owner_id = ? GROUP BY species ORDER BY count(*) DESC, species LIMIT 1
                """, String.class, subject.userId());
        return pets.isEmpty() ? null : new Profile(pets.get(0), null, "Gợi ý theo hồ sơ thú cưng của bạn.");
    }

    private List<Pick> popular(int limit) {
        return db.query("""
                SELECT p.id, p.name, p.price, p.available_stock FROM product p JOIN category c ON c.id = p.category_id
                WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE' AND p.available_stock > 0
                ORDER BY (SELECT coalesce(sum(i.quantity), 0) FROM order_item i JOIN customer_order o ON o.id = i.order_id
                          WHERE i.product_id = p.id AND o.status <> 'CANCELLED'
                            AND o.created_at > now() - interval '30 days') DESC, p.name, p.id
                LIMIT ?
                """, (rs, row) -> new Pick(rs.getString(1), rs.getString(2), rs.getBigDecimal(3).toPlainString(),
                rs.getInt(4)), limit);
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<?> rejected(Rejected error) {
        return error(error.status, error.code, error.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed() {
        return error(400, "INVALID_REQUEST", "Yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể xử lý.");
    }

    static ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message,
                "fieldErrors", List.of(), "traceId", UUID.randomUUID().toString()));
    }

    record Recommendations(UUID recommendationId, boolean personalized, List<Item> items) {}
    record Item(UUID impressionId, String productId, String name, String price, int availableStock, String link,
            String reason) {}
    private record Pick(String id, String name, String price, int availableStock) {}
    private record Profile(String species, String lifeStage, String reason) {}
    private record Parsed(UUID eventId, String name, Long productId, UUID impressionId, Map<String, Object> metadata,
            Instant clientTime) {}

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
