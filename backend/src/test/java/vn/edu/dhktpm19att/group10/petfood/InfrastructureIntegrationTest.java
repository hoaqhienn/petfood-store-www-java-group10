package vn.edu.dhktpm19att.group10.petfood;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class InfrastructureIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SessionRepository<? extends Session> sessions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @Transactional
    void guestCartUsesRedisSessionAndRejectsStaleMutations() throws Exception {
        Long category = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Cart', 'cart-test', 'ACTIVE') RETURNING id
                """, Long.class);
        long product = insertProduct(category, "CART-TEST-1", "ACTIVE");
        jdbc.update("UPDATE product SET available_stock = 8 WHERE id = ?", product);

        var first = mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(0)).andReturn();
        var session = first.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        var csrfResponse = mvc.perform(get("/api/v1/auth/csrf").cookie(session))
                .andExpect(status().isOk()).andReturn();
        var csrf = json.readTree(csrfResponse.getResponse().getContentAsString());
        String header = csrf.get("headerName").asText();
        String token = csrf.get("token").asText();
        String add = """
                {"productId":"%d","quantity":2,"expectedRevision":0}
                """.formatted(product);
        mvc.perform(post("/api/v1/cart/items").cookie(session).contentType(MediaType.APPLICATION_JSON).content(add))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cart/items").cookie(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content(add))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.subtotal").value("56000"));
        mvc.perform(post("/api/v1/cart/items").cookie(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content(add))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_CHANGED"));
        mvc.perform(get("/api/v1/cart").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(patch("/api/v1/cart/items/{id}", product).cookie(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity":3,"expectedRevision":1}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(3));
        mvc.perform(delete("/api/v1/cart/items/{id}", product).cookie(session).header(header, token)
                        .param("expectedRevision", "2"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/cart").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.items").isEmpty());

        mvc.perform(post("/api/v1/cart/items").cookie(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content(add.replace(":0", ":3")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register").cookie(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"displayName":"Test User","email":" CartUser@example.test ","password":"demo-password"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.emailStatus").value("PENDING"));
        var login = mvc.perform(post("/api/v1/auth/login").cookie(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"cartuser@example.test","password":"demo-password"}
                                """))
                .andExpect(status().isOk()).andReturn();
        var rotated = login.getResponse().getCookie("SESSION");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(session.getValue());
        mvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").cookie(rotated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("cartuser@example.test"));
        mvc.perform(get("/api/v1/cart").cookie(rotated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(post("/api/v1/auth/logout").cookie(rotated).header(header, token))
                .andExpect(status().isForbidden());
        var freshCsrf = json.readTree(mvc.perform(get("/api/v1/auth/csrf").cookie(rotated))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/checkout/preview").cookie(rotated)
                        .header(header, freshCsrf.get("token").asText()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartRevision\":3}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_CHANGED"));
        var preview = mvc.perform(post("/api/v1/checkout/preview").cookie(rotated)
                        .header(header, freshCsrf.get("token").asText()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartRevision\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quoteToken").isString())
                .andExpect(jsonPath("$.grandTotal").value("56000"))
                .andExpect(jsonPath("$.items[0].quantity").value(2)).andReturn();
        assertThat(jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?",
                Integer.class, product)).isEqualTo(8);
        var quote = json.readTree(preview.getResponse().getContentAsString());
        String body = """
                {"cartToken":"%s","cartRevision":4,"quoteToken":"%s","paymentMethod":"COD",
                 "recipient":{"name":"Test User","phone":"0900000000","address":"Local QA"}}
                """.formatted(quote.get("cartToken").asText(), quote.get("quoteToken").asText());
        String key = java.util.UUID.randomUUID().toString();
        var order = mvc.perform(post("/api/v1/orders").cookie(rotated).header(header, freshCsrf.get("token").asText())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paymentStatus").value("UNPAID")).andReturn();
        String orderId = json.readTree(order.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/v1/admin/orders").cookie(rotated)).andExpect(status().isForbidden());
        Long adminId = jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES ('admin-orders@example.test', 'Admin', 'unused', 'ADMIN', 'ACTIVE') RETURNING id
                """, Long.class);
        var admin = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(adminId.toString()).roles("ADMIN");
        mvc.perform(get("/api/v1/admin/orders").with(admin).param("customer", "cartuser@example.test")
                        .param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].order.id").value(orderId));
        mvc.perform(get("/api/v1/admin/orders/{id}", orderId).with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderId));
        jdbc.update("UPDATE user_account SET status = 'LOCKED' WHERE id = ?", adminId);
        mvc.perform(get("/api/v1/admin/orders").with(admin)).andExpect(status().isForbidden());
        jdbc.update("UPDATE product SET name = 'Updated catalog name', price = 30000 WHERE id = ?", product);
        mvc.perform(get("/api/v1/orders").cookie(rotated).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(orderId));
        mvc.perform(get("/api/v1/orders/{id}", orderId).cookie(rotated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipient.address").value("Local QA"))
                .andExpect(jsonPath("$.items[0].name").value("Test food"))
                .andExpect(jsonPath("$.items[0].unitPrice").value("28000"));
        mvc.perform(get("/api/v1/orders/{id}", orderId)).andExpect(status().isUnauthorized());
        Long otherId = jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES ('other-history@example.test', 'Other', 'unused', 'CUSTOMER', 'ACTIVE') RETURNING id
                """, Long.class);
        mvc.perform(get("/api/v1/orders/{id}", orderId).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(otherId.toString()).roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders").cookie(rotated).header(header, freshCsrf.get("token").asText())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderId));
        assertThat(jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?",
                Integer.class, product)).isEqualTo(6);
        mvc.perform(get("/api/v1/cart").cookie(rotated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        String cancellation = "{\"reason\":\"Changed my mind\",\"version\":0}";
        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).cookie(rotated)
                        .header(header, freshCsrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(cancellation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).cookie(rotated)
                        .header(header, freshCsrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(cancellation))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?",
                Integer.class, product)).isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                SELECT reason FROM order_history WHERE order_id = ? AND action = 'ORDER_CANCELLED'
                """, String.class, java.util.UUID.fromString(orderId))).isEqualTo("Changed my mind");
        mvc.perform(post("/api/v1/auth/logout").cookie(rotated).header(header, freshCsrf.get("token").asText()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").cookie(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void catalogShowsSoldOutProductsButHidesInactiveProductsAndCategories() throws Exception {
        Long active = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Test', 'catalog-test-active', 'ACTIVE') RETURNING id
                """, Long.class);
        Long hidden = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Hidden', 'catalog-test-hidden', 'INACTIVE') RETURNING id
                """, Long.class);
        long soldOut = insertProduct(active, "CATALOG-TEST-1", "ACTIVE");
        long hiddenProduct = insertProduct(active, "CATALOG-TEST-2", "INACTIVE");
        long hiddenCategoryProduct = insertProduct(hidden, "CATALOG-TEST-3", "ACTIVE");

        mvc.perform(get("/api/v1/products").param("q", "catalog-test-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(soldOut)))
                .andExpect(jsonPath("$.items[0].availableStock").value(0))
                .andExpect(jsonPath("$.items[0].price").value("28000"));
        mvc.perform(get("/api/v1/products/{id}", soldOut)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/{id}", hiddenProduct)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/{id}", hiddenCategoryProduct)).andExpect(status().isNotFound());
    }

    private long insertProduct(long categoryId, String sku, String status) {
        return jdbc.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage,
                                     brand, weight_grams, price, available_stock, status)
                VALUES (?, ?, 'Test food', 'Fixture', 'CAT', 'ADULT', 'Test', 85, 28000, 0, ?)
                RETURNING id
                """, Long.class, categoryId, sku, status);
    }

    @Test
    void flywayEnablesVectorExtension() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void sessionSurvivesRedisRoundTrip() {
        verifySessionRoundTrip(sessions);
    }

    private <S extends Session> void verifySessionRoundTrip(SessionRepository<S> repository) {
        S session = repository.createSession();
        session.setAttribute("scaffold-check", "ok");
        try {
            repository.save(session);
            S restored = repository.findById(session.getId());
            assertThat(restored).isNotNull();
            assertThat((String) restored.getAttribute("scaffold-check")).isEqualTo("ok");
        } finally {
            repository.deleteById(session.getId());
        }
    }
}

