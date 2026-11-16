package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hiding a category while a customer checks out (AC36). Both sides lock the category row first, so whichever
 * commits first decides: a hide that wins makes checkout fail, a checkout that wins keeps a valid order.
 * Needs committed data and two real transactions, so rows use the hide36 prefix and are removed afterwards.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class HideDuringCheckoutIntegrationTest {
    private static final String PASSWORD = "hide-password";

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    long category;
    long product;

    @BeforeEach
    void fixture() {
        cleanup();
        category = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Hide', 'hide36-test', 'ACTIVE') RETURNING id
                """, Long.class);
        product = jdbc.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage,
                                     brand, weight_grams, price, available_stock, status)
                VALUES (?, 'HIDE36-1', 'Hide food', 'Fixture', 'CAT', 'ADULT', 'Test', 400, 28000, 5, 'ACTIVE')
                RETURNING id
                """, Long.class, category);
    }

    @AfterEach
    void cleanup() {
        String users = "SELECT id FROM user_account WHERE email_normalized LIKE 'hide36-%@example.test'";
        String orders = "SELECT id FROM customer_order WHERE customer_id IN (" + users + ")";
        jdbc.update("DELETE FROM notification_outbox WHERE aggregate_id IN (" + users + ")");
        jdbc.update("DELETE FROM inventory_movement WHERE sku_snapshot = 'HIDE36-1'");
        jdbc.update("DELETE FROM order_history WHERE order_id IN (" + orders + ")");
        jdbc.update("DELETE FROM checkout_request WHERE customer_id IN (" + users + ")");
        jdbc.update("DELETE FROM order_item WHERE order_id IN (" + orders + ")");
        jdbc.update("DELETE FROM customer_order WHERE customer_id IN (" + users + ")");
        jdbc.update("DELETE FROM user_account WHERE email_normalized LIKE 'hide36-%@example.test'");
        jdbc.update("DELETE FROM product WHERE sku = 'HIDE36-1'");
        jdbc.update("DELETE FROM category WHERE slug = 'hide36-test'");
    }

    @Test
    void hideCommittedFirstMakesTheWaitingCheckoutFail() throws Exception {
        var buyer = buyer("hide36-late@example.test");
        var locked = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            // An admin transaction hides the category and keeps its lock for a moment before committing.
            var hide = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                jdbc.update("UPDATE category SET status = 'INACTIVE', version = version + 1 WHERE id = ?", category);
                locked.countDown();
                try {
                    Thread.sleep(800);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var response = placeOrder(buyer); // waits on the category lock, then sees it hidden
            hide.get(10, TimeUnit.SECONDS);
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(json.readTree(response.getContentAsString()).get("code").asText())
                    .isEqualTo("PRODUCT_UNAVAILABLE");
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?", Integer.class, product))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item WHERE product_id = ?", Integer.class, product))
                .isZero();
    }

    @Test
    void checkoutCommittedFirstKeepsAValidOrderAfterTheHide() throws Exception {
        var buyer = buyer("hide36-early@example.test");
        var response = placeOrder(buyer);
        assertThat(response.getStatus()).isEqualTo(201);
        String orderId = json.readTree(response.getContentAsString()).get("id").asText();

        jdbc.update("UPDATE category SET status = 'INACTIVE', version = version + 1 WHERE id = ?", category);
        mvc.perform(get("/api/v1/products/{id}", product)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/{id}", orderId).cookie(buyer.session()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT name_snapshot FROM order_item WHERE order_id = ? AND product_id = ?
                """, String.class, UUID.fromString(orderId), product)).isEqualTo("Hide food");
        assertThat(jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?", Integer.class, product))
                .isEqualTo(4);
    }

    private Buyer buyer(String email) throws Exception {
        jdbc.update("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES (?, 'Hide buyer', ?, 'CUSTOMER', 'ACTIVE')
                """, email, passwords.encode(PASSWORD));
        Cookie guest = mvc.perform(get("/api/v1/cart")).andReturn().getResponse().getCookie("SESSION");
        JsonNode csrf = csrf(guest);
        mvc.perform(post("/api/v1/cart/items").cookie(guest)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%d\",\"quantity\":1,\"expectedRevision\":0}".formatted(product)))
                .andExpect(status().isOk());
        Cookie session = mvc.perform(post("/api/v1/auth/login").cookie(guest)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        JsonNode fresh = csrf(session);
        JsonNode quote = json.readTree(mvc.perform(post("/api/v1/checkout/preview").cookie(session)
                        .header(fresh.get("headerName").asText(), fresh.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cartRevision\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Buyer(session, fresh.get("headerName").asText(), fresh.get("token").asText(), quote);
    }

    private MockHttpServletResponse placeOrder(Buyer buyer) throws Exception {
        String body = """
                {"cartToken":"%s","cartRevision":%d,"quoteToken":"%s","paymentMethod":"COD",
                 "recipient":{"name":"Hide buyer","phone":"0900000000","address":"Local QA"}}
                """.formatted(buyer.quote().get("cartToken").asText(), buyer.quote().get("cartRevision").asLong(),
                buyer.quote().get("quoteToken").asText());
        return mvc.perform(post("/api/v1/orders").cookie(buyer.session()).header(buyer.header(), buyer.token())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private JsonNode csrf(Cookie session) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andReturn().getResponse()
                .getContentAsString());
    }

    private record Buyer(Cookie session, String header, String token, JsonNode quote) {}
}
