package vn.edu.dhktpm19att.group10.petfood.account;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminNotificationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MockMvc mvc;

    long adminId;
    long customer;
    UUID order;
    UUID mail;
    JavaMailSender smtp;
    RegistrationMailWorker worker;

    @BeforeEach
    void fixture() {
        adminId = account("mail-admin@example.test", "ADMIN");
        customer = account("mail-customer@example.test", "CUSTOMER");
        order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, order_code, customer_id, recipient_name, phone, address_text,
                                            subtotal, shipping_fee, grand_total)
                VALUES (?, ?, ?, 'Receiver', '0900000000', 'Local QA', 28000, 0, 28000)
                """, order, "PF-" + order, customer);
        mail = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification_outbox(id, type, aggregate_id, payload)
                VALUES (?, 'ORDER_PLACED', ?, jsonb_build_object('orderCode', CAST(? AS text)))
                """, mail, customer, "PF-" + order);
        // A worker wired to a fake SMTP server. The test transaction hides these rows from the scheduled worker.
        smtp = mock(JavaMailSender.class);
        worker = new RegistrationMailWorker(jdbc, transactions, smtp, "noreply@petfood.test");
    }

    @Test
    void smtpFailureKeepsOrderAndAdminRetryResendsWithoutDuplicatingIt() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(smtp).send(any(SimpleMailMessage.class));
        for (int attempt = 1; attempt <= 3; attempt++) {
            jdbc.update("UPDATE notification_outbox SET next_attempt_at = now() - interval '1 second' WHERE id = ?", mail);
            worker.sendDueMail();
            assertThat(jdbc.queryForObject("SELECT attempts FROM notification_outbox WHERE id = ?", Integer.class, mail))
                    .isEqualTo(attempt);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, mail))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE id = ?", Integer.class, order))
                .isEqualTo(1);

        mvc.perform(get("/api/v1/admin/notifications").with(admin()).param("status", "FAILED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mail.toString()))
                .andExpect(jsonPath("$.items[0].recipient").value("m***@example.test"))
                .andExpect(jsonPath("$.items[0].orderCode").value("PF-" + order))
                .andExpect(jsonPath("$.items[0].attempts").value(3));
        mvc.perform(post("/api/v1/admin/notifications/{id}/retry", mail).with(admin()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/notifications/{id}/retry", mail).with(admin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));
        mvc.perform(post("/api/v1/admin/notifications/{id}/retry", mail).with(admin()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FAILED"));

        reset(smtp);
        doNothing().when(smtp).send(any(SimpleMailMessage.class));
        worker.sendDueMail();
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("mail-customer@example.test");
        assertThat(sent.getValue().getText()).contains("PF-" + order).contains("thanh toán khi nhận hàng");
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, mail))
                .isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE customer_id = ?", Integer.class,
                customer)).isEqualTo(1);
    }

    @Test
    void onlyActiveAdminsSeeNotifications() throws Exception {
        mvc.perform(get("/api/v1/admin/notifications").with(user(Long.toString(customer)).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/notifications/{id}/retry", UUID.randomUUID()).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE user_account SET status = 'LOCKED' WHERE id = ?", adminId);
        mvc.perform(get("/api/v1/admin/notifications").with(admin()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void maskKeepsOnlyFirstLetterAndDomain() {
        assertThat(AdminNotificationController.mask("abc@example.test")).isEqualTo("a***@example.test");
        assertThat(AdminNotificationController.mask(null)).isNull();
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
