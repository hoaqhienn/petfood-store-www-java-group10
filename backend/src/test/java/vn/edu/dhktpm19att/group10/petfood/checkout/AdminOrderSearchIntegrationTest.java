package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin order search by code, Vietnam-time date range, newest first and stable paging (AC20). */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderSearchIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    long admin;
    long customer;

    @BeforeEach
    void fixture() {
        admin = account("search-admin@example.test", "ADMIN");
        customer = account("search-customer@example.test", "CUSTOMER");
        // 2026-11-12 23:30 in Vietnam is still 12/11 locally although it is 16:30 UTC.
        order("PF-SEARCH-0001", "PENDING", "2026-11-10T10:00:00+07:00");
        order("PF-SEARCH-0002", "CONFIRMED", "2026-11-12T08:00:00+07:00");
        order("PF-SEARCH-0003", "CANCELLED", "2026-11-12T23:30:00+07:00");
        order("PF-OTHER-0004", "PENDING", "2026-11-13T00:30:00+07:00");
    }

    @Test
    void searchesByCodeDateAndStatusNewestFirst() throws Exception {
        search("orderCode", "pf-search").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].order.orderCode").value("PF-SEARCH-0003"))
                .andExpect(jsonPath("$.items[2].order.orderCode").value("PF-SEARCH-0001"));
        search("dateFrom", "2026-11-12", "dateTo", "2026-11-12")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].order.orderCode").value("PF-SEARCH-0003"))
                .andExpect(jsonPath("$.items[1].order.orderCode").value("PF-SEARCH-0002"));
        search("dateFrom", "2026-11-13", "dateTo", "2026-11-13")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].order.orderCode").value("PF-OTHER-0004"));
        search("status", "PENDING").andExpect(jsonPath("$.totalElements").value(2));
        search("size", "1", "page", "1")
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.items[0].order.orderCode").value("PF-SEARCH-0003"));
        search("dateFrom", "2026-11-13", "dateTo", "2026-11-12").andExpect(status().isBadRequest());
        search("status", "LOST").andExpect(status().isBadRequest());
        search("dateFrom", "13/11/2026").andExpect(status().isBadRequest());
    }

    private ResultActions search(String... params) throws Exception {
        var request = get("/api/v1/admin/orders").with(user(Long.toString(admin)).roles("ADMIN"))
                .param("customer", "search-customer@example.test");
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mvc.perform(request);
    }

    private void order(String code, String status, String createdAt) {
        jdbc.update("""
                INSERT INTO customer_order (id, order_code, customer_id, status, recipient_name, phone, address_text,
                                            subtotal, shipping_fee, grand_total, created_at)
                VALUES (?, ?, ?, ?, 'Receiver', '0900000000', 'Local QA', 0, 0, 0, CAST(? AS timestamptz))
                """, UUID.randomUUID(), code, customer, status, createdAt);
    }

    private long account(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES (?, 'Fixture', 'unused', ?, 'ACTIVE') RETURNING id
                """, Long.class, email, role);
    }
}
