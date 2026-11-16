package vn.edu.dhktpm19att.group10.petfood.account;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration rules (AC05, AC06, AC33). The duplicate-email race needs two real transactions, so this class
 * commits its rows and removes every reg-* account afterwards.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    @AfterEach
    void cleanup() {
        String users = "SELECT id FROM user_account WHERE email_normalized LIKE 'reg-%@example.test'";
        jdbc.update("DELETE FROM notification_outbox WHERE aggregate_id IN (" + users + ")");
        jdbc.update("DELETE FROM user_account WHERE email_normalized LIKE 'reg-%@example.test'");
    }

    @Test
    void equivalentEmailsRegisteredAtTheSameTimeCreateOneAccount() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var results = new ArrayList<Future<Integer>>();
            for (String email : List.of("Reg-Race@Example.test", "  reg-race@example.test ")) {
                Callable<Integer> register = () -> {
                    start.await();
                    return register(email, "secret-password", "").andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(register));
            }
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var result : results) statuses.add(result.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM user_account WHERE email_normalized = 'reg-race@example.test'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification_outbox o JOIN user_account u ON u.id = o.aggregate_id
                WHERE u.email_normalized = 'reg-race@example.test' AND o.type = 'REGISTRATION'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void registrationCreatesHashedCustomerAndIgnoresPrivilegedFields() throws Exception {
        register("reg-plain@example.test", "secret-password", ",\"role\":\"ADMIN\",\"status\":\"LOCKED\"")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.emailStatus").value("PENDING"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM user_account WHERE email_normalized = 'reg-plain@example.test'", String.class);
        assertThat(hash).doesNotContain("secret-password");
        assertThat(passwords.matches("secret-password", hash)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT role FROM user_account WHERE email_normalized = 'reg-plain@example.test'
                """, String.class)).isEqualTo("CUSTOMER");

        register("reg-short@example.test", "short", "").andExpect(status().isUnprocessableEntity());
        register("not-an-email", "secret-password", "").andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account WHERE email_normalized = 'reg-short@example.test'",
                Integer.class)).isZero();
    }

    private ResultActions register(String email, String password, String extra) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Reg user\",\"email\":\"%s\",\"password\":\"%s\"%s}"
                        .formatted(email, password, extra)));
    }
}
