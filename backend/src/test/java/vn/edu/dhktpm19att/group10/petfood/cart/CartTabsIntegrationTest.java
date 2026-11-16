package vn.edu.dhktpm19att.group10.petfood.cart;

import java.util.ArrayList;
import java.util.Map;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Two browser tabs share one Session cart (AC31). Needs committed catalog rows, so it is not @Transactional. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class CartTabsIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SessionCartStore carts;

    long product;

    @BeforeEach
    void fixture() {
        cleanup();
        long category = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Tabs', 'tabs-test', 'ACTIVE') RETURNING id
                """, Long.class);
        product = jdbc.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage,
                                     brand, weight_grams, price, available_stock, status)
                VALUES (?, 'TABS-1', 'Tab food', 'Fixture', 'CAT', 'ADULT', 'Test', 85, 28000, 20, 'ACTIVE')
                RETURNING id
                """, Long.class, category);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM product WHERE sku = 'TABS-1'");
        jdbc.update("DELETE FROM category WHERE slug = 'tabs-test'");
    }

    @Test
    void twoTabsWritingTheSameRevisionLetOnlyOneWin() throws Exception {
        Cookie session = mvc.perform(get("/api/v1/cart")).andExpect(status().isOk()).andReturn()
                .getResponse().getCookie("SESSION");
        JsonNode csrf = json.readTree(mvc.perform(get("/api/v1/auth/csrf").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String body = "{\"productId\":\"%d\",\"quantity\":1,\"expectedRevision\":0}".formatted(product);

        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var results = new ArrayList<Future<Integer>>();
            for (int tab = 0; tab < 2; tab++) {
                Callable<Integer> add = () -> {
                    start.await();
                    return mvc.perform(post("/api/v1/cart/items").cookie(session)
                                    .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(add));
            }
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var result : results) statuses.add(result.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        // The losing tab did not add a second unit on top of the winner.
        mvc.perform(get("/api/v1/cart").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void checkoutCleanupKeepsCartChangedInAnotherTab() {
        var session = new MockHttpSession();
        session.setMaxInactiveInterval(1800);
        session.setAttribute("petfood.cartKey", "tabs-test-" + java.util.UUID.randomUUID());
        var bought = carts.read(session);
        var purchased = new SessionCartStore.Cart(bought.cart().cartToken(), 1, Map.of(Long.toString(product), 2));
        assertThat(carts.replace(session, bought, purchased)).isTrue();
        // Another tab edits the cart after the order for revision 1 was placed, before cleanup runs.
        var edited = carts.read(session);
        var newer = new SessionCartStore.Cart(purchased.cartToken(), 2, Map.of(Long.toString(product), 3));
        assertThat(carts.replace(session, edited, newer)).isTrue();

        carts.clearIfCurrent(session, purchased.cartToken(), 1);
        assertThat(carts.readCart(session)).isEqualTo(newer);

        carts.clearIfCurrent(session, purchased.cartToken(), 2);
        assertThat(carts.readCart(session).quantities()).isEmpty();
        assertThat(carts.readCart(session).revision()).isEqualTo(3);
    }
}
