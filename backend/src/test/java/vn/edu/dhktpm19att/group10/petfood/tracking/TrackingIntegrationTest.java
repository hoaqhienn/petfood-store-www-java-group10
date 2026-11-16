package vn.edu.dhktpm19att.group10.petfood.tracking;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrackingIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    long catAdult;
    long catAdult2;
    long dogAdult;
    long catSoldOut;

    @BeforeEach
    void fixture() {
        long category = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Tracking', 'tracking-test', 'ACTIVE') RETURNING id
                """, Long.class);
        catAdult = product(category, "TRACK-CAT-1", "CAT", "ADULT", 5);
        catAdult2 = product(category, "TRACK-CAT-2", "CAT", "ADULT", 5);
        dogAdult = product(category, "TRACK-DOG-1", "DOG", "ADULT", 5);
        catSoldOut = product(category, "TRACK-CAT-3", "CAT", "ADULT", 0);
    }

    @Test
    void withoutConsentNothingIsCollectedAndRecommendationsArePopular() throws Exception {
        Cookie session = session();
        mvc.perform(get("/api/v1/consent").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analytics").value(false))
                .andExpect(jsonPath("$.personalization").value(false));
        events(session, "[" + event("product_view", catAdult) + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.stored").value(0))
                .andExpect(jsonPath("$.ignored").value(1));
        mvc.perform(get("/api/v1/recommendations").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personalized").value(false));
        assertThat(count("SELECT count(*) FROM behavior_event")).isZero();
        assertThat(count("SELECT count(*) FROM recommendation_impression")).isZero();
    }

    @Test
    void consentedEventsAreWhitelistedDedupedAndFreeOfRawText() throws Exception {
        Cookie session = session();
        consent(session, true, false).andExpect(status().isOk()).andExpect(jsonPath("$.analytics").value(true));
        String view = event("product_view", catAdult);
        String body = "[" + view + "," + view
                + ",{\"eventId\":\"" + UUID.randomUUID() + "\",\"name\":\"order_placed\"}"
                + ",{\"eventId\":\"not-a-uuid\",\"name\":\"page_view\"}"
                + ",{\"eventId\":\"" + UUID.randomUUID() + "\",\"name\":\"search\",\"metadata\":"
                + "{\"query\":\"giao tới a@example.test 0900000000\",\"species\":\"CAT\",\"resultCount\":3}}"
                + ",{\"eventId\":\"" + UUID.randomUUID() + "\",\"name\":\"page_view\",\"metadata\":"
                + "{\"path\":\"/orders?email=a@example.test\"}}]";
        events(session, body).andExpect(status().isOk()).andExpect(jsonPath("$.stored").value(3))
                .andExpect(jsonPath("$.duplicates").value(1)).andExpect(jsonPath("$.rejected").value(2));
        assertThat(jdbc.queryForObject("SELECT metadata::text FROM behavior_event WHERE name = 'search'", String.class))
                .contains("CAT").doesNotContain("example.test").doesNotContain("0900000000");
        assertThat(jdbc.queryForObject("SELECT metadata::text FROM behavior_event WHERE name = 'page_view'",
                String.class)).isEqualTo("{}");
        assertThat(count("SELECT count(*) FROM behavior_event WHERE name = 'order_placed'")).isZero();
    }

    @Test
    void personalizedRecommendationsUseOnlyOwnHistoryAndCountClicksOnIssuedImpressions() throws Exception {
        Cookie session = session();
        consent(session, true, true).andExpect(status().isOk());
        events(session, "[" + event("product_view", catAdult) + "," + event("product_view", catAdult) + "]")
                .andExpect(jsonPath("$.stored").value(2));
        // Another visitor's dog views must not influence this visitor's suggestions.
        Cookie other = session();
        consent(other, true, true);
        events(other, "[" + event("product_view", dogAdult) + "," + event("product_view", dogAdult) + ","
                + event("product_view", dogAdult) + "]");

        JsonNode recs = json.readTree(mvc.perform(get("/api/v1/recommendations").cookie(session).param("limit", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personalized").value(true))
                .andReturn().getResponse().getContentAsString());
        assertThat(recs.findValuesAsText("productId"))
                .containsOnly(Long.toString(catAdult), Long.toString(catAdult2))
                .doesNotContain(Long.toString(catSoldOut));
        String impression = recs.get("items").get(0).get("impressionId").asText();

        String click = "{\"eventId\":\"%s\",\"name\":\"recommendation_click\",\"impressionId\":\"%s\"}";
        events(session, "[" + click.formatted(UUID.randomUUID(), impression) + ","
                + click.formatted(UUID.randomUUID(), impression) + ","
                + click.formatted(UUID.randomUUID(), UUID.randomUUID()) + "]")
                .andExpect(jsonPath("$.stored").value(2)).andExpect(jsonPath("$.rejected").value(1));
        events(other, "[" + click.formatted(UUID.randomUUID(), impression) + "]")
                .andExpect(jsonPath("$.rejected").value(1));

        long admin = account("track-admin@example.test", "ADMIN");
        mvc.perform(get("/api/v1/admin/analytics").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ctr.impressions").value(recs.get("items").size()))
                .andExpect(jsonPath("$.ctr.clicked").value(1))
                .andExpect(jsonPath("$.consentingVisitors").value(2))
                .andExpect(jsonPath("$.definitions.ctr").isString());
    }

    @Test
    void withdrawingConsentDeletesCollectedDataButKeepsOrders() throws Exception {
        Cookie session = session();
        consent(session, true, true);
        events(session, "[" + event("product_view", catAdult) + "]").andExpect(jsonPath("$.stored").value(1));
        mvc.perform(get("/api/v1/recommendations").cookie(session)).andExpect(status().isOk());
        long customer = account("track-buyer@example.test", "CUSTOMER");
        UUID order = order(customer, "PENDING", null);

        consent(session, false, false).andExpect(status().isOk()).andExpect(jsonPath("$.analytics").value(false));
        assertThat(count("SELECT count(*) FROM behavior_event")).isZero();
        assertThat(count("SELECT count(*) FROM recommendation_impression")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE id = ?", Integer.class, order))
                .isEqualTo(1);
        events(session, "[" + event("product_view", catAdult) + "]").andExpect(jsonPath("$.stored").value(0));
    }

    @Test
    void guestHistoryIsNotMergedIntoTheAccountAndPetsGuideSignedInSuggestions() throws Exception {
        Cookie session = session();
        consent(session, true, true);
        events(session, "[" + event("product_view", dogAdult) + "]").andExpect(jsonPath("$.stored").value(1));

        long customer = account("track-pets@example.test", "CUSTOMER");
        // Signed in on the same Session: the account has its own (default: no) consent.
        events(session, as(customer, "CUSTOMER"), "[" + event("product_view", dogAdult) + "]")
                .andExpect(jsonPath("$.stored").value(0));
        assertThat(count("SELECT count(*) FROM behavior_event WHERE user_id IS NOT NULL")).isZero();

        jdbc.update("INSERT INTO pet_profile (owner_id, name, species) VALUES (?, 'Milo', 'CAT')", customer);
        mvc.perform(put("/api/v1/consent").cookie(session).with(as(customer, "CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"analytics\":false,\"personalization\":true}"))
                .andExpect(status().isOk());
        JsonNode recs = json.readTree(mvc.perform(get("/api/v1/recommendations").cookie(session)
                        .with(as(customer, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personalized").value(true))
                .andReturn().getResponse().getContentAsString());
        assertThat(recs.findValuesAsText("productId")).doesNotContain(Long.toString(dogAdult));
        assertThat(count("SELECT count(*) FROM recommendation_impression")).isZero();
    }

    @Test
    void funnelCountsSessionsInOrderAndRevenueOnlyCompletedPaidOrders() throws Exception {
        insert("x", "product_view", 1, false);
        insert("x", "add_to_cart", 2, false);
        insert("x", "checkout_start", 3, false);
        insert("x", "order_placed", 4, false);
        insert("y", "add_to_cart", 1, false); // added to cart before viewing: only the view step counts
        insert("y", "product_view", 2, false);
        insert("z", "product_view", 1, false);
        insert("z", "checkout_start", 2, false);
        insert("demo", "product_view", 1, true);
        insert("demo", "add_to_cart", 2, true);
        long customer = account("track-revenue@example.test", "CUSTOMER");
        order(customer, "COMPLETED", "PAID");
        order(customer, "PENDING", null);
        order(customer, "CANCELLED", null);

        long admin = account("track-funnel-admin@example.test", "ADMIN");
        mvc.perform(get("/api/v1/admin/analytics").with(as(admin, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.demoIncluded").value(false))
                .andExpect(jsonPath("$.funnel.product_view").value(3))
                .andExpect(jsonPath("$.funnel.add_to_cart").value(1))
                .andExpect(jsonPath("$.funnel.checkout_start").value(1))
                .andExpect(jsonPath("$.funnel.order_placed").value(1))
                .andExpect(jsonPath("$.demoEventsInWindow").value(2))
                .andExpect(jsonPath("$.completedRevenue").value("50000"));
        mvc.perform(get("/api/v1/admin/analytics").with(as(admin, "ADMIN")).param("includeDemo", "true"))
                .andExpect(jsonPath("$.funnel.product_view").value(4))
                .andExpect(jsonPath("$.funnel.add_to_cart").value(2));
        mvc.perform(get("/api/v1/admin/analytics").with(as(admin, "ADMIN"))
                        .param("dateFrom", "2026-11-10").param("dateTo", "2026-11-01"))
                .andExpect(status().isBadRequest());
        long customerOnly = account("track-not-admin@example.test", "CUSTOMER");
        mvc.perform(get("/api/v1/admin/analytics").with(as(customerOnly, "CUSTOMER"))).andExpect(status().isForbidden());
    }

    private Cookie session() throws Exception {
        return mvc.perform(get("/api/v1/consent")).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie("SESSION");
    }

    private ResultActions consent(Cookie session, boolean analytics, boolean personalization) throws Exception {
        return mvc.perform(put("/api/v1/consent").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"analytics\":%s,\"personalization\":%s}".formatted(analytics, personalization)));
    }

    private ResultActions events(Cookie session, String array) throws Exception {
        return mvc.perform(post("/api/v1/events").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":" + array + "}"));
    }

    private ResultActions events(Cookie session, RequestPostProcessor actor, String array) throws Exception {
        return mvc.perform(post("/api/v1/events").cookie(session).with(actor).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"events\":" + array + "}"));
    }

    private static String event(String name, long product) {
        return "{\"eventId\":\"%s\",\"name\":\"%s\",\"productId\":\"%d\"}".formatted(UUID.randomUUID(), name, product);
    }

    private void insert(String session, String name, int minute, boolean demo) {
        jdbc.update("""
                INSERT INTO behavior_event (event_id, name, subject_key, anon_token, server_received_at, is_demo)
                VALUES (?, ?, ?, ?, now() - interval '1 hour' + (? * interval '1 minute'), ?)
                """, UUID.randomUUID(), name, "anon:" + session, session, minute, demo);
    }

    private UUID order(long customer, String status, String payment) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, order_code, customer_id, status, payment_status, recipient_name, phone,
                                            address_text, subtotal, shipping_fee, grand_total, paid_at)
                VALUES (?, ?, ?, ?, ?, 'Receiver', '0900000000', 'Local QA', 50000, 0, 50000, ?)
                """, id, "PF-" + id, customer, status, payment == null ? "UNPAID" : payment,
                payment == null ? null : java.sql.Timestamp.from(java.time.Instant.now()));
        return id;
    }

    private long product(long category, String sku, String species, String lifeStage, int stock) {
        return jdbc.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage,
                                     brand, weight_grams, price, available_stock, status)
                VALUES (?, ?, 'Tracking food', 'Fixture', ?, ?, 'Test', 400, 50000, ?, 'ACTIVE') RETURNING id
                """, Long.class, category, sku, species, lifeStage, stock);
    }

    private long account(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES (?, 'Fixture', 'unused', ?, 'ACTIVE') RETURNING id
                """, Long.class, email, role);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static RequestPostProcessor as(long id, String role) {
        return user(Long.toString(id)).roles(role);
    }
}
