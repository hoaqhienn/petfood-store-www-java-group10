package vn.edu.dhktpm19att.group10.petfood.tracking;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Opt-in tracking (BR24-BR26). A visitor is identified by the signed-in account or, for guests, by a random token
 * kept in the Session (never the Session id, IP or a browser fingerprint). Nothing is stored without consent.
 */
@Service
public class Tracking {
    static final String POLICY_VERSION = "2026-11";
    private static final String TOKEN = "petfood.trackingToken";

    private final JdbcTemplate db;
    private final ObjectMapper json;

    Tracking(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    static Subject subject(HttpSession session, Authentication auth) {
        Object token = session.getAttribute(TOKEN);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(TOKEN, token);
        }
        Long user = auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()
                ? null : Long.valueOf(auth.getName());
        return new Subject(user, token.toString());
    }

    Consent consent(Subject subject) {
        var rows = db.query("""
                SELECT analytics, personalization, updated_at FROM consent_preference WHERE subject_key = ?
                """, (rs, row) -> new Consent(rs.getBoolean(1), rs.getBoolean(2), POLICY_VERSION,
                rs.getObject(3, java.time.OffsetDateTime.class).toInstant()), subject.key());
        return rows.isEmpty() ? new Consent(false, false, POLICY_VERSION, null) : rows.get(0);
    }

    /**
     * Records an event that only the backend may emit, such as order_placed (BR25). Never throws: tracking must
     * not turn a committed order into an error.
     */
    public void serverEvent(HttpSession session, Authentication auth, String name, Map<String, Object> metadata) {
        try {
            var subject = subject(session, auth);
            if (!consent(subject).analytics()) return;
            insert(subject, UUID.randomUUID(), name, null, null, metadata, null);
        } catch (RuntimeException ignored) {
            // Analytics is optional; the business action already succeeded.
        }
    }

    int insert(Subject subject, UUID eventId, String name, Long productId, UUID impressionId,
            Map<String, Object> metadata, java.time.Instant clientTime) {
        try {
            return db.update("""
                    INSERT INTO behavior_event (event_id, name, subject_key, anon_token, user_id, product_id,
                                                impression_id, metadata, client_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                    ON CONFLICT (event_id) DO NOTHING
                    """, eventId, name, subject.key(), subject.anonToken(), subject.userId(), productId, impressionId,
                    json.writeValueAsString(metadata), clientTime == null ? null
                            : java.sql.Timestamp.from(clientTime));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    record Subject(Long userId, String anonToken) {
        String key() {
            return userId != null ? "user:" + userId : "anon:" + anonToken;
        }
    }

    record Consent(boolean analytics, boolean personalization, String policyVersion, java.time.Instant updatedAt) {}
}
