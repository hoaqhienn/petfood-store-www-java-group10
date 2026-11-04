package vn.edu.dhktpm19at.group10.petfood;

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

