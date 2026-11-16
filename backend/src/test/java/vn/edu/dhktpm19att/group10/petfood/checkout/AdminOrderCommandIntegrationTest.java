package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.util.UUID;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderCommandIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    long customer;
    long adminId;
    long category;
    long product;
    UUID order;

    @BeforeEach
    void fixture() {
        customer = account("order-customer@example.test", "CUSTOMER");
        adminId = account("order-admin@example.test", "ADMIN");
        category = jdbc.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES ('Admin order', 'admin-order-test', 'ACTIVE') RETURNING id
                """, Long.class);
        // Stock 8 after the fixture order below took 2 units.
        product = jdbc.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage,
                                     brand, weight_grams, price, available_stock, status)
                VALUES (?, 'ADMIN-ORDER-1', 'Test food', 'Fixture', 'CAT', 'ADULT', 'Test', 85, 28000, 8, 'ACTIVE')
                RETURNING id
                """, Long.class, category);
        order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, order_code, customer_id, recipient_name, phone, address_text,
                                            subtotal, shipping_fee, grand_total)
                VALUES (?, ?, ?, 'Receiver', '0900000000', 'Local QA', 56000, 0, 56000)
                """, order, "PF-" + order, customer);
        jdbc.update("""
                INSERT INTO order_item (order_id, product_id, sku_snapshot, name_snapshot, unit_price, quantity, line_total)
                VALUES (?, ?, 'ADMIN-ORDER-1', 'Test food', 28000, 2, 56000)
                """, order, product);
    }

    @Test
    void adminMovesOrderThroughStateMachineAndCompletesCodOnlyWhenPaid() throws Exception {
        transition("SHIPPING", 0, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
        transition("CONFIRMED", 0, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.version").value(1));
        transition("SHIPPING", 0, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        transition("SHIPPING", 1, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPING"));
        mvc.perform(patch("/api/v1/admin/orders/{id}/items/{p}", order, product).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"reason\":\"Late edit\",\"version\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
        transition("CANCELLED", 2, "Too late").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
        transition("COMPLETED", 2, null).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT payment_status FROM customer_order WHERE id = ?", String.class, order))
                .isEqualTo("UNPAID");
        mvc.perform(post("/api/v1/admin/orders/{id}/transitions", order).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"COMPLETED\",\"version\":2,\"codReceived\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID")).andExpect(jsonPath("$.paidAt").isNotEmpty());
        transition("CANCELLED", 3, "Undo").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM order_history WHERE order_id = ? AND action = 'STATUS_CHANGED'
                    AND from_status = 'SHIPPING' AND to_status = 'COMPLETED' AND actor_id = ?
                """, Integer.class, order, adminId)).isEqualTo(1);
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void adminCancelsConfirmedOrderAndRestoresStockOnce() throws Exception {
        transition("CONFIRMED", 0, null).andExpect(status().isOk());
        transition("CANCELLED", 1, "").andExpect(status().isUnprocessableEntity());
        transition("CANCELLED", 1, "Customer called").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        transition("CANCELLED", 2, "Again").andExpect(status().isConflict());
        assertThat(stock()).isEqualTo(10);
        assertThat(jdbc.queryForObject("""
                SELECT sum(delta) FROM inventory_movement WHERE order_id = ? AND reason = 'ORDER_CANCELLED'
                """, Integer.class, order)).isEqualTo(2);
    }

    @Test
    void adminChangesQuantityWithStockDeltaAndRecalculatedTotals() throws Exception {
        quantity("5", 0).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.items[0].unitPrice").value("28000"))
                .andExpect(jsonPath("$.subtotal").value("140000")).andExpect(jsonPath("$.grandTotal").value("140000"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(stock()).isEqualTo(5);

        quantity("11", 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        quantity("0", 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        quantity("2.5", 1).andExpect(status().isUnprocessableEntity());
        quantity("3", 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(stock()).isEqualTo(5);

        jdbc.update("UPDATE product SET status = 'INACTIVE', price = 99000 WHERE id = ?", product);
        quantity("6", 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
        quantity("1", 1).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.subtotal").value("28000")).andExpect(jsonPath("$.version").value(2));
        assertThat(stock()).isEqualTo(9);
        assertThat(jdbc.queryForObject("""
                SELECT sum(delta) FROM inventory_movement WHERE order_id = ? AND reason = 'ORDER_ADJUSTED'
                """, Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT quantity_change FROM order_history WHERE order_id = ? AND action = 'QUANTITY_CHANGED'
                ORDER BY id DESC LIMIT 1
                """, Integer.class, order)).isEqualTo(-4);

        mvc.perform(patch("/api/v1/admin/orders/{id}/items/{p}", order, product + 1000).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"reason\":\"Wrong line\",\"version\":2}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_ITEM_NOT_FOUND"));
    }

    @Test
    void onlyActiveAdminsCanChangeOrders() throws Exception {
        String body = "{\"toStatus\":\"CONFIRMED\",\"version\":0}";
        mvc.perform(post("/api/v1/admin/orders/{id}/transitions", order).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/orders/{id}/transitions", order).with(csrf())
                        .with(user(Long.toString(customer)).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/orders/{id}/transitions", order).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE user_account SET status = 'LOCKED' WHERE id = ?", adminId);
        transition("CONFIRMED", 0, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, order))
                .isEqualTo("PENDING");
    }

    private ResultActions transition(String target, long version, String reason) throws Exception {
        String reasonJson = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        return mvc.perform(post("/api/v1/admin/orders/{id}/transitions", order).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"" + target + "\",\"version\":" + version + reasonJson + "}"));
    }

    private ResultActions quantity(String value, long version) throws Exception {
        return mvc.perform(patch("/api/v1/admin/orders/{id}/items/{p}", order, product).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + value + ",\"reason\":\"Customer request\",\"version\":" + version + "}"));
    }

    private RequestPostProcessor admin() {
        return user(Long.toString(adminId)).roles("ADMIN");
    }

    private int stock() {
        return jdbc.queryForObject("SELECT available_stock FROM product WHERE id = ?", Integer.class, product);
    }

    private long account(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES (?, 'Fixture', 'unused', ?, 'ACTIVE') RETURNING id
                """, Long.class, email, role);
    }
}
