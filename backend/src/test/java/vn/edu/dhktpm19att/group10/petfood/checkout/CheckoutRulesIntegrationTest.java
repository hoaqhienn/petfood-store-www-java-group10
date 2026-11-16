package vn.edu.dhktpm19att.group10.petfood.checkout;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checkout validation, idempotency and replay ownership (AC02, AC09, AC13, AC32, AC35). */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CheckoutRulesIntegrationTest {
    private static final String PASSWORD = "rules-password";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    long product;

    @BeforeEach
    void fixture() {
        long category = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Checkout rules', 'checkout-rules', 'ACTIVE')
                RETURNING id
                """, Long.class);
        product = jdbc.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage,
                                     brand, weight_grams, price, available_stock, status)
                VALUES (?, 'RULES-1', 'Rules food', 'Fixture', 'CAT', 'ADULT', 'Test', 400, 28000, 10, 'ACTIVE')
                RETURNING id
                """, Long.class, category);
    }

    @Test
    void emptyCartAndSoldOutCartCannotBeQuoted() throws Exception {
        var empty = buyer("rules-empty@example.test", false);
        preview(empty, 0).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CART_EMPTY"));

        var late = buyer("rules-late@example.test", false);
        addOne(late);
        jdbc.update("UPDATE product SET available_stock = 0 WHERE id = ?", product);
        preview(late, 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void invalidRecipientOrChangedPriceIsRejectedWithoutAnOrder() throws Exception {
        var buyer = buyer("rules-recipient@example.test", true);
        order(buyer, key(), body(buyer, "Rules buyer", "abc", "Local QA"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        order(buyer, key(), body(buyer, "Rules buyer", "0900000000", "  "))
                .andExpect(status().isUnprocessableEntity());
        order(buyer, key(), body(buyer, "", "0900000000", "Local QA"))
                .andExpect(status().isUnprocessableEntity());
        order(buyer, "bad key with spaces", body(buyer, "Rules buyer", "0900000000", "Local QA"))
                .andExpect(status().isBadRequest());

        // The client cannot set the price; a price change after the quote asks the customer to review again.
        jdbc.update("UPDATE product SET price = 30000 WHERE id = ?", product);
        order(buyer, key(), body(buyer, "Rules buyer", "0900000000", "Local QA"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item WHERE product_id = ?", Integer.class, product))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?", Integer.class, product))
                .isEqualTo(10);
    }

    @Test
    void sameKeyWithDifferentPayloadConflicts() throws Exception {
        var buyer = buyer("rules-key@example.test", true);
        String key = key();
        String id = json.readTree(order(buyer, key, body(buyer, "Rules buyer", "0900000000", "Local QA"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        order(buyer, key, body(buyer, "Rules buyer", "0900000000", "Another address"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        order(buyer, key, body(buyer, "Rules buyer", "0900000000", "Local QA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertThat(jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?", Integer.class, product))
                .isEqualTo(9);
    }

    @Test
    void anotherAccountOrLockedOwnerCannotReplayAnOrderKey() throws Exception {
        var owner = buyer("rules-owner@example.test", true);
        String key = key();
        String ownerBody = body(owner, "Rules owner", "0900000000", "Owner address");
        String orderId = json.readTree(order(owner, key, ownerBody).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // Someone else reusing the key and body gets nothing back from the owner's order.
        var other = buyer("rules-other@example.test", true);
        var stolen = order(other, key, ownerBody).andExpect(status().isConflict()).andReturn().getResponse();
        assertThat(stolen.getContentAsString()).doesNotContain(orderId).doesNotContain("Owner address");
        mvc.perform(get("/api/v1/orders/{id}", orderId).cookie(other.session()))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE user_account SET status = 'LOCKED' WHERE email_normalized = 'rules-owner@example.test'");
        order(owner, key, ownerBody).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE id = ?", Integer.class,
                UUID.fromString(orderId))).isEqualTo(1);
    }

    private Buyer buyer(String email, boolean withQuote) throws Exception {
        jdbc.update("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES (?, 'Rules buyer', ?, 'CUSTOMER', 'ACTIVE')
                """, email, passwords.encode(PASSWORD));
        Cookie guest = mvc.perform(get("/api/v1/cart")).andReturn().getResponse().getCookie("SESSION");
        JsonNode csrf = csrf(guest);
        Cookie session = mvc.perform(post("/api/v1/auth/login").cookie(guest)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        JsonNode fresh = csrf(session);
        var buyer = new Buyer(session, fresh.get("headerName").asText(), fresh.get("token").asText(), null);
        if (!withQuote) return buyer;
        addOne(buyer);
        JsonNode quote = json.readTree(preview(buyer, 1).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        return new Buyer(session, buyer.header(), buyer.token(), quote);
    }

    private void addOne(Buyer buyer) throws Exception {
        mvc.perform(post("/api/v1/cart/items").cookie(buyer.session()).header(buyer.header(), buyer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%d\",\"quantity\":1,\"expectedRevision\":0}".formatted(product)))
                .andExpect(status().isOk());
    }

    private ResultActions preview(Buyer buyer, long revision) throws Exception {
        return mvc.perform(post("/api/v1/checkout/preview").cookie(buyer.session()).header(buyer.header(), buyer.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cartRevision\":" + revision + "}"));
    }

    private ResultActions order(Buyer buyer, String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/orders").cookie(buyer.session()).header(buyer.header(), buyer.token())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(Buyer buyer, String name, String phone, String address) {
        return """
                {"cartToken":"%s","cartRevision":%d,"quoteToken":"%s","paymentMethod":"COD",
                 "recipient":{"name":"%s","phone":"%s","address":"%s"}}
                """.formatted(buyer.quote().get("cartToken").asText(), buyer.quote().get("cartRevision").asLong(),
                buyer.quote().get("quoteToken").asText(), name, phone, address);
    }

    private JsonNode csrf(Cookie session) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andReturn().getResponse()
                .getContentAsString());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private record Buyer(Cookie session, String header, String token, JsonNode quote) {}
}
