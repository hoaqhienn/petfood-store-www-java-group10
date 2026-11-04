package vn.edu.dhktpm19at.group10.petfood;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class InfrastructureIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SessionRepository<? extends Session> sessions;
    @Autowired MockMvc mvc;

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

