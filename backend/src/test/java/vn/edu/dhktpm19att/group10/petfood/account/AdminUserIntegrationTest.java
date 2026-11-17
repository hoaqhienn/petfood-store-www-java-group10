package vn.edu.dhktpm19att.group10.petfood.account;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    long adminId;
    long customer;

    @BeforeEach
    void fixture() {
        adminId = account("users-admin@example.test", "ADMIN");
        customer = account("users-customer@example.test", "CUSTOMER");
    }

    @Test
    void adminSearchesAndReadsUsersWithoutPasswordHash() throws Exception {
        mvc.perform(get("/api/v1/admin/users").with(admin()).param("q", "USERS-CUSTOMER").param("role", "CUSTOMER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(customer)))
                .andExpect(jsonPath("$.items[0].email").value("users-customer@example.test"))
                .andExpect(jsonPath("$.items[0].orderCount").value(0))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/admin/users/{id}", customer).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/admin/users").with(admin()).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").with(admin()).param("q", "users-customer").param("status", "")
                        .param("role", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/admin/users/{id}", "999999999999")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users/{id}", customer).with(user(Long.toString(customer)).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdatesWhitelistedFieldsAndLockTakesEffectOnNextRequest() throws Exception {
        update(customer, "{\"displayName\":\" New Name \",\"phone\":\"0911222333\",\"version\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.phone").value("0911222333")).andExpect(jsonPath("$.version").value(1));
        update(customer, "{\"role\":\"ADMIN\",\"version\":1}").andExpect(status().isUnprocessableEntity());
        update(customer, "{\"email\":\"x@example.test\",\"version\":1}").andExpect(status().isUnprocessableEntity());
        update(customer, "{\"status\":\"DELETED\",\"version\":1}").andExpect(status().isUnprocessableEntity());
        update(customer, "{\"displayName\":\"Stale\",\"version\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT role FROM user_account WHERE id = ?", String.class, customer))
                .isEqualTo("CUSTOMER");

        mvc.perform(get("/api/v1/orders").with(user(Long.toString(customer)).roles("CUSTOMER")))
                .andExpect(status().isOk());
        update(customer, "{\"status\":\"LOCKED\",\"version\":1}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCKED"));
        mvc.perform(get("/api/v1/orders").with(user(Long.toString(customer)).roles("CUSTOMER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void adminCannotLockOrDeleteThemselves() throws Exception {
        update(adminId, "{\"status\":\"LOCKED\",\"version\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_PROTECTED"));
        mvc.perform(delete("/api/v1/admin/users/{id}", adminId).with(admin()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_PROTECTED"));

        long other = account("users-admin-2@example.test", "ADMIN");
        update(other, "{\"status\":\"LOCKED\",\"version\":0}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM user_account WHERE id = ?", String.class, adminId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void adminDeletesOnlyUsersWithoutOrders() throws Exception {
        UUID order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, order_code, customer_id, status, recipient_name, phone, address_text,
                                            subtotal, shipping_fee, grand_total)
                VALUES (?, ?, ?, 'CANCELLED', 'Receiver', '0900000000', 'Local QA', 0, 0, 0)
                """, order, "PF-" + order, customer);
        mvc.perform(delete("/api/v1/admin/users/{id}", customer).with(admin()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_HAS_ORDERS"));

        long fresh = account("users-fresh@example.test", "CUSTOMER");
        UUID mail = UUID.randomUUID();
        jdbc.update("INSERT INTO notification_outbox(id, type, aggregate_id) VALUES (?, 'REGISTRATION', ?)", mail, fresh);
        mvc.perform(delete("/api/v1/admin/users/{id}", fresh).with(admin())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/users/{id}", fresh).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account WHERE id = ?", Integer.class, fresh))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, mail))
                .isEqualTo("CANCELLED");
        mvc.perform(delete("/api/v1/admin/users/{id}", fresh).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private ResultActions update(long id, String body) throws Exception {
        return mvc.perform(patch("/api/v1/admin/users/{id}", id).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private RequestPostProcessor admin() {
        return user(Long.toString(adminId)).roles("ADMIN");
    }

    private long account(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES (?, 'Fixture', 'unused', ?, 'ACTIVE') RETURNING id
                """, Long.class, email, role);
    }
}
