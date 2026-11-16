package vn.edu.dhktpm19att.group10.petfood.catalog;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCatalogIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    long adminId;

    @BeforeEach
    void fixture() {
        adminId = jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES ('catalog-admin@example.test', 'Admin', 'unused', 'ADMIN', 'ACTIVE') RETURNING id
                """, Long.class);
    }

    @Test
    void adminManagesCategoriesAndCannotDeleteNonEmptyOne() throws Exception {
        String category = id(send(post("/api/v1/admin/categories"),
                "{\"name\":\"Hạt cho mèo\",\"slug\":\"admin-cat-food\",\"status\":\"ACTIVE\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.productCount").value(0)));
        send(post("/api/v1/admin/categories"), "{\"name\":\"Copy\",\"slug\":\"admin-cat-food\",\"status\":\"ACTIVE\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"));
        send(post("/api/v1/admin/categories"), "{\"name\":\"Bad\",\"slug\":\"Có Dấu\",\"status\":\"ACTIVE\"}")
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/admin/categories").with(admin()).param("q", "admin-cat"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        String product = createProduct(category, "ADMIN-CAT-1", 3);
        mvc.perform(get("/api/v1/products/{id}", product)).andExpect(status().isOk());
        send(patch("/api/v1/admin/categories/{id}", category), "{\"status\":\"INACTIVE\",\"version\":5}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(patch("/api/v1/admin/categories/{id}", category), "{\"status\":\"INACTIVE\",\"version\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.productCount").value(1));
        mvc.perform(get("/api/v1/products/{id}", product)).andExpect(status().isNotFound());

        jdbc.update("UPDATE product SET status = 'INACTIVE' WHERE id = ?", Long.parseLong(product));
        mvc.perform(delete("/api/v1/admin/categories/{id}", category).with(admin()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));
        mvc.perform(delete("/api/v1/admin/products/{id}", product).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/categories/{id}", category).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/categories/{id}", category).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void adminCreatesAndEditsProductsWithoutTouchingStockDirectly() throws Exception {
        String category = category("admin-product-cat");
        String product = createProduct(category, "admin-prod-1", 5);
        mvc.perform(get("/api/v1/admin/products/{id}", product).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("ADMIN-PROD-1"))
                .andExpect(jsonPath("$.availableStock").value(5)).andExpect(jsonPath("$.price").value("185000"));
        assertThat(jdbc.queryForObject("SELECT sum(delta) FROM inventory_movement WHERE product_id = ?",
                Integer.class, Long.parseLong(product))).isEqualTo(5);

        send(post("/api/v1/admin/products"), productBody(category, "ADMIN-PROD-1", "185000"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"));
        send(post("/api/v1/admin/products"), productBody(category, "ADMIN-PROD-2", "0"))
                .andExpect(status().isUnprocessableEntity());
        send(post("/api/v1/admin/products"), productBody(category, "ADMIN-PROD-3", "1.5"))
                .andExpect(status().isUnprocessableEntity());
        send(patch("/api/v1/admin/products/{id}", product), "{\"availableStock\":99,\"version\":0}")
                .andExpect(status().isUnprocessableEntity());

        send(patch("/api/v1/admin/products/{id}", product),
                "{\"name\":\"Hạt mới\",\"price\":\"190000\",\"status\":\"INACTIVE\",\"version\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Hạt mới"))
                .andExpect(jsonPath("$.price").value("190000")).andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.contentVersion").value(1)).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.availableStock").value(5));
        mvc.perform(get("/api/v1/admin/products").with(admin()).param("q", "admin-prod").param("status", "INACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/products/{id}", product)).andExpect(status().isNotFound());
    }

    @Test
    void stockAdjustmentsAreAuditedAndNeverNegative() throws Exception {
        String category = category("admin-stock-cat");
        String product = createProduct(category, "ADMIN-STOCK-1", 5);
        String path = "/api/v1/admin/products/{id}/stock-adjustments";
        send(post(path, product), "{\"delta\":-6,\"reason\":\"Kiểm kê\",\"version\":0}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_STOCK"));
        send(post(path, product), "{\"delta\":0,\"reason\":\"Kiểm kê\",\"version\":0}")
                .andExpect(status().isUnprocessableEntity());
        send(post(path, product), "{\"delta\":3,\"version\":0}").andExpect(status().isUnprocessableEntity());

        // A sale bumps the product version, so the stale admin form cannot overwrite the new stock.
        jdbc.update("UPDATE product SET available_stock = available_stock - 1, version = version + 1 WHERE id = ?",
                Long.parseLong(product));
        send(post(path, product), "{\"delta\":3,\"reason\":\"Nhập hàng\",\"version\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(post(path, product), "{\"delta\":3,\"reason\":\"Nhập hàng\",\"version\":1}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableStock").value(7))
                .andExpect(jsonPath("$.version").value(2));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM inventory_movement WHERE product_id = ? AND delta = 3 AND actor_id = ?
                    AND reason = 'ADMIN_ADJUSTMENT: Nhập hàng'
                """, Integer.class, Long.parseLong(product), adminId)).isEqualTo(1);
    }

    @Test
    void productInAnyOrderCannotBeDeletedButUnusedOneKeepsMovementSnapshot() throws Exception {
        String category = category("admin-delete-cat");
        String ordered = createProduct(category, "ADMIN-DEL-1", 2);
        String unused = createProduct(category, "ADMIN-DEL-2", 4);
        long customer = jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES ('catalog-buyer@example.test', 'Buyer', 'unused', 'CUSTOMER', 'ACTIVE') RETURNING id
                """, Long.class);
        UUID order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, order_code, customer_id, status, recipient_name, phone, address_text,
                                            subtotal, shipping_fee, grand_total)
                VALUES (?, ?, ?, 'CANCELLED', 'Receiver', '0900000000', 'Local QA', 185000, 0, 185000)
                """, order, "PF-" + order, customer);
        jdbc.update("""
                INSERT INTO order_item (order_id, product_id, sku_snapshot, name_snapshot, unit_price, quantity, line_total)
                VALUES (?, ?, 'ADMIN-DEL-1', 'Test', 185000, 1, 185000)
                """, order, Long.parseLong(ordered));

        mvc.perform(delete("/api/v1/admin/products/{id}", ordered).with(admin()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_REFERENCED"));
        mvc.perform(delete("/api/v1/admin/products/{id}", unused).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM inventory_movement WHERE product_id IS NULL AND sku_snapshot = 'ADMIN-DEL-2'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void customersAndLockedAdminsCannotManageCatalog() throws Exception {
        mvc.perform(post("/api/v1/admin/categories").with(user("1").roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"slug\":\"x-locked\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE user_account SET status = 'LOCKED' WHERE id = ?", adminId);
        send(post("/api/v1/admin/categories"), "{\"name\":\"X\",\"slug\":\"x-locked\",\"status\":\"ACTIVE\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM category WHERE slug = 'x-locked'", Integer.class))
                .isZero();
    }

    private String category(String slug) throws Exception {
        return id(send(post("/api/v1/admin/categories"),
                "{\"name\":\"Test\",\"slug\":\"" + slug + "\",\"status\":\"ACTIVE\"}")
                .andExpect(status().isCreated()));
    }

    private String createProduct(String category, String sku, int stock) throws Exception {
        String body = productBody(category, sku, "185000").replace("}", ",\"initialStock\":" + stock + "}");
        return id(send(post("/api/v1/admin/products"), body).andExpect(status().isCreated()));
    }

    private static String productBody(String category, String sku, String price) {
        return """
                {"categoryId":"%s","sku":"%s","name":"Hạt test","description":"Fixture","species":"CAT",
                 "lifeStage":"ADULT","brand":"Test","weightGrams":1500,"price":"%s","status":"ACTIVE"}
                """.formatted(category, sku, price).strip();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mvc.perform(request.with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private RequestPostProcessor admin() {
        return user(Long.toString(adminId)).roles("ADMIN");
    }
}
