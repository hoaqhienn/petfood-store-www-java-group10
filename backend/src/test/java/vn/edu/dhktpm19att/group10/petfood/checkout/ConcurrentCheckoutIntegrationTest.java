package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.dhktpm19att.group10.petfood.cart.SessionCartStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Checkout races need committed data and real concurrent transactions, so this class is not @Transactional.
 * Every row it creates uses the race- prefix and is removed after each test.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentCheckoutIntegrationTest {
    private static final String PASSWORD = "race-password";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean RedisConnectionFactory redis;
    @MockitoSpyBean SessionCartStore carts;

    long category;

    @BeforeEach
    void fixture() {
        cleanup();
        category = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Race', 'race-test', 'ACTIVE') RETURNING id
                """, Long.class);
    }

    @AfterEach
    void cleanup() {
        String users = "SELECT id FROM user_account WHERE email_normalized LIKE 'race-%@example.test'";
        String orders = "SELECT id FROM customer_order WHERE customer_id IN (" + users + ")";
        jdbc.update("DELETE FROM notification_outbox WHERE aggregate_id IN (" + users + ")");
        jdbc.update("DELETE FROM inventory_movement WHERE sku_snapshot LIKE 'RACE-%'");
        jdbc.update("DELETE FROM order_history WHERE order_id IN (" + orders + ")");
        jdbc.update("DELETE FROM checkout_request WHERE customer_id IN (" + users + ")");
        jdbc.update("DELETE FROM order_item WHERE order_id IN (" + orders + ")");
        jdbc.update("DELETE FROM customer_order WHERE customer_id IN (" + users + ")");
        jdbc.update("DELETE FROM user_account WHERE email_normalized LIKE 'race-%@example.test'");
        jdbc.update("DELETE FROM product WHERE sku LIKE 'RACE-%'");
        jdbc.update("DELETE FROM category WHERE slug = 'race-test'");
    }

    @Test
    void redisUnavailableBeforeCheckoutPreservesCartAndStock() throws Exception {
        long product = product("RACE-REDIS-BEFORE", 3);
        var buyer = buyer("race-redis-before@example.test", product);
        doThrow(new RedisConnectionFailureException("AC22: Redis unavailable")).when(redis).getConnection();
        try {
            var response = placeOrder(buyer);
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(json.readTree(response.getContentAsString()).path("code").asText())
                    .isEqualTo("DEPENDENCY_UNAVAILABLE");
            assertThat(stock(product)).isEqualTo(3);
            assertThat(orderCount(product)).isZero();
        } finally {
            doCallRealMethod().when(redis).getConnection();
        }
        mvc.perform(get("/api/v1/cart").cookie(buyer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(buyer.quote().get("cartRevision").asInt()))
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void redisCleanupFailureAfterCommitReturnsOrderAndRetryCleansCart() throws Exception {
        long product = product("RACE-REDIS-AFTER", 3);
        var buyer = buyer("race-redis-after@example.test", product);
        String key = UUID.randomUUID().toString();
        doAnswer(invocation -> {
            assertThat(orderCount(product)).isEqualTo(1);
            doThrow(new RedisConnectionFailureException("AC22: Redis unavailable after commit"))
                    .when(redis).getConnection();
            return invocation.callRealMethod();
        }).doCallRealMethod().when(carts).clearIfCurrent(any(), anyString(), anyLong());

        MockHttpServletResponse created;
        try {
            created = placeOrder(buyer, key);
        } finally {
            doCallRealMethod().when(redis).getConnection();
        }
        assertThat(created.getStatus()).isEqualTo(201);
        String orderId = json.readTree(created.getContentAsString()).get("id").asText();
        assertThat(orderCount(product)).isEqualTo(1);
        assertThat(stock(product)).isEqualTo(2);

        var retry = placeOrder(buyer, key);
        assertThat(retry.getStatus()).isEqualTo(200);
        assertThat(json.readTree(retry.getContentAsString()).get("id").asText()).isEqualTo(orderId);
        assertThat(orderCount(product)).isEqualTo(1);
        assertThat(stock(product)).isEqualTo(2);
        mvc.perform(get("/api/v1/cart").cookie(buyer.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    private int orderCount(long product) {
        return jdbc.queryForObject("SELECT count(*) FROM order_item WHERE product_id = ?", Integer.class, product);
    }

    @Test
    void twoCustomersBuyingTheLastUnitProduceExactlyOneOrder() throws Exception {
        long product = product("RACE-LAST-1", 1);
        var buyers = List.of(buyer("race-a@example.test", product), buyer("race-b@example.test", product));

        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var results = new ArrayList<Future<MockHttpServletResponse>>();
            for (var buyer : buyers) {
                Callable<MockHttpServletResponse> order = () -> {
                    start.await();
                    return placeOrder(buyer);
                };
                results.add(pool.submit(order));
            }
            start.countDown();
            var statuses = new ArrayList<Integer>();
            var codes = new ArrayList<String>();
            for (var result : results) {
                var response = result.get(60, TimeUnit.SECONDS);
                statuses.add(response.getStatus());
                if (response.getStatus() != 201) {
                    codes.add(json.readTree(response.getContentAsString()).get("code").asText());
                }
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
            assertThat(codes).containsExactly("INSUFFICIENT_STOCK");
        } finally {
            pool.shutdownNow();
        }

        assertThat(stock(product)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item WHERE product_id = ?", Integer.class, product))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(delta) FROM inventory_movement WHERE product_id = ?",
                Integer.class, product)).isEqualTo(-1);
    }

    @Test
    void orderWithOneShortLineRollsBackEveryLine() throws Exception {
        long plenty = product("RACE-MULTI-1", 5);
        long scarce = product("RACE-MULTI-2", 1);
        var buyer = buyer("race-multi@example.test", plenty, scarce);
        // Another sale empties the scarce SKU after the quote was issued.
        jdbc.update("UPDATE product SET available_stock = 0, version = version + 1 WHERE id = ?", scarce);

        var response = placeOrder(buyer);
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(json.readTree(response.getContentAsString()).get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stock(plenty)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM customer_order o JOIN user_account u ON u.id = o.customer_id
                WHERE u.email_normalized = 'race-multi@example.test'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE sku_snapshot LIKE 'RACE-MULTI-%'",
                Integer.class)).isZero();
    }

    /** Logs a customer in with a cart holding one unit of each product and a fresh checkout quote. */
    private Buyer buyer(String email, long... products) throws Exception {
        jdbc.update("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES (?, 'Race buyer', ?, 'CUSTOMER', 'ACTIVE')
                """, email, passwords.encode(PASSWORD));
        Cookie session = mvc.perform(get("/api/v1/cart")).andExpect(status().isOk()).andReturn()
                .getResponse().getCookie("SESSION");
        var csrf = csrf(session);
        long revision = 0;
        for (long product : products) {
            mvc.perform(post("/api/v1/cart/items").cookie(session).header(csrf.get("headerName").asText(),
                            csrf.get("token").asText()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"%d\",\"quantity\":1,\"expectedRevision\":%d}"
                                    .formatted(product, revision)))
                    .andExpect(status().isOk());
            revision++;
        }
        Cookie loggedIn = mvc.perform(post("/api/v1/auth/login").cookie(session)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        var fresh = csrf(loggedIn);
        var quote = json.readTree(mvc.perform(post("/api/v1/checkout/preview").cookie(loggedIn)
                        .header(fresh.get("headerName").asText(), fresh.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cartRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Buyer(loggedIn, fresh.get("headerName").asText(), fresh.get("token").asText(), quote);
    }

    private MockHttpServletResponse placeOrder(Buyer buyer) throws Exception {
        return placeOrder(buyer, UUID.randomUUID().toString());
    }

    private MockHttpServletResponse placeOrder(Buyer buyer, String key) throws Exception {
        String body = """
                {"cartToken":"%s","cartRevision":%d,"quoteToken":"%s","paymentMethod":"COD",
                 "recipient":{"name":"Race buyer","phone":"0900000000","address":"Local QA"}}
                """.formatted(buyer.quote().get("cartToken").asText(), buyer.quote().get("cartRevision").asLong(),
                buyer.quote().get("quoteToken").asText());
        return mvc.perform(post("/api/v1/orders").cookie(buyer.session()).header(buyer.csrfHeader(), buyer.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private JsonNode csrf(Cookie session) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long product(String sku, int stock) {
        return jdbc.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage,
                                     brand, weight_grams, price, available_stock, status)
                VALUES (?, ?, 'Race food', 'Fixture', 'CAT', 'ADULT', 'Test', 85, 28000, ?, 'ACTIVE')
                RETURNING id
                """, Long.class, category, sku, stock);
    }

    private int stock(long product) {
        return jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?", Integer.class, product);
    }

    private record Buyer(Cookie session, String csrfHeader, String csrfToken, JsonNode quote) {}
}
