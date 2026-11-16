package vn.edu.dhktpm19att.group10.petfood.cart;

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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public catalog filters (AC01) and cart quantity, stock and size rules (AC02, AC04). */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogAndCartRulesIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    long category;

    @BeforeEach
    void fixture() {
        category = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Rules', 'rules-test', 'ACTIVE') RETURNING id
                """, Long.class);
    }

    @Test
    void catalogFiltersPagesAndSortsVisibleProducts() throws Exception {
        product("AC01-DOG-ADULT", "DOG", "ADULT", 100000, 5, "ACTIVE");
        product("AC01-CAT-ADULT", "CAT", "ADULT", 200000, 5, "ACTIVE");
        product("AC01-CAT-JUNIOR", "CAT", "JUNIOR", 300000, 5, "ACTIVE");
        product("AC01-DOG-SENIOR", "DOG", "SENIOR", 50000, 5, "ACTIVE");

        products("q", "ac01-", "species", "CAT").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        products("q", "ac01-", "species", "CAT", "lifeStage", "JUNIOR")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("AC01-CAT-JUNIOR"));
        products("q", "ac01-", "minPrice", "100000", "maxPrice", "250000")
                .andExpect(jsonPath("$.totalElements").value(2));
        products("q", "ac01-", "sort", "price,desc", "size", "1", "page", "1")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("AC01-CAT-ADULT"))
                .andExpect(jsonPath("$.totalPages").value(4));
        products("q", "ac01-zzz").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.items").isEmpty());
        products("q", "ac01-", "sort", "stock,desc").andExpect(status().isBadRequest());
        products("minPrice", "300", "maxPrice", "100").andExpect(status().isBadRequest());
        products("size", "101").andExpect(status().isBadRequest());

        jdbc.update("UPDATE category SET status = 'INACTIVE' WHERE id = ?", category);
        products("q", "ac01-").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidQuantitiesNeverChangeTheCart() throws Exception {
        long food = product("AC04-FOOD", "CAT", "ADULT", 28000, 200, "ACTIVE");
        var tab = tab();
        for (String quantity : new String[]{"0", "-1", "2.5", "100", "\"3\"", "null"}) {
            add(tab, food, quantity, 0).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        }
        mvc.perform(get("/api/v1/cart").cookie(tab.session()))
                .andExpect(jsonPath("$.revision").value(0)).andExpect(jsonPath("$.items").isEmpty());

        add(tab, food, "99", 0).andExpect(status().isOk());
        add(tab, food, "1", 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        mvc.perform(patch("/api/v1/cart/items/{id}", food).cookie(tab.session()).header(tab.header(), tab.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0,\"expectedRevision\":1}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/cart").cookie(tab.session()))
                .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.items[0].quantity").value(99));
    }

    @Test
    void soldOutHiddenAndFiftyFirstProductsCannotBeAdded() throws Exception {
        long soldOut = product("AC02-SOLD-OUT", "CAT", "ADULT", 28000, 0, "ACTIVE");
        long hidden = product("AC02-HIDDEN", "CAT", "ADULT", 28000, 5, "INACTIVE");
        var tab = tab();
        add(tab, soldOut, "1", 0).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        add(tab, hidden, "1", 0).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));

        for (int i = 0; i < 50; i++) {
            add(tab, product("AC04-MANY-" + i, "DOG", "ADULT", 10000, 5, "ACTIVE"), "1", i)
                    .andExpect(status().isOk());
        }
        add(tab, product("AC04-MANY-50", "DOG", "ADULT", 10000, 5, "ACTIVE"), "1", 50)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CART_LIMIT"));
        mvc.perform(get("/api/v1/cart").cookie(tab.session()))
                .andExpect(jsonPath("$.revision").value(50)).andExpect(jsonPath("$.items.length()").value(50));
    }

    private ResultActions products(String... params) throws Exception {
        var request = get("/api/v1/products");
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mvc.perform(request);
    }

    private ResultActions add(Tab tab, long product, String quantity, long revision) throws Exception {
        return mvc.perform(post("/api/v1/cart/items").cookie(tab.session()).header(tab.header(), tab.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"%d\",\"quantity\":%s,\"expectedRevision\":%d}"
                        .formatted(product, quantity, revision)));
    }

    private Tab tab() throws Exception {
        Cookie session = mvc.perform(get("/api/v1/cart")).andReturn().getResponse().getCookie("SESSION");
        JsonNode csrf = json.readTree(mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andReturn()
                .getResponse().getContentAsString());
        return new Tab(session, csrf.get("headerName").asText(), csrf.get("token").asText());
    }

    private long product(String sku, String species, String lifeStage, int price, int stock, String status) {
        return jdbc.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage,
                                     brand, weight_grams, price, available_stock, status)
                VALUES (?, ?, ?, 'Fixture', ?, ?, 'Test', 400, ?, ?, ?) RETURNING id
                """, Long.class, category, sku, "Rules " + sku, species, lifeStage, price, stock, status);
    }

    private record Tab(Cookie session, String header, String token) {}
}
