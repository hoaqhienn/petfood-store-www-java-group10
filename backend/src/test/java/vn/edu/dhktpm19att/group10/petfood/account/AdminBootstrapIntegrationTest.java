package vn.edu.dhktpm19att.group10.petfood.account;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
class AdminBootstrapIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    @Transactional
    void bootstrapCreatesOneAdminAndNeverResetsIt() {
        var first = new AdminBootstrap(jdbc, passwords, " Boot-Admin@Example.test ", "first-password", "Boot Admin");
        assertThat(first.bootstrap()).isEqualTo(AdminBootstrap.Outcome.CREATED);
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM user_account WHERE email_normalized = 'boot-admin@example.test'", String.class);
        assertThat(hash).isNotEqualTo("first-password");
        assertThat(passwords.matches("first-password", hash)).isTrue();

        var restart = new AdminBootstrap(jdbc, passwords, "boot-admin@example.test", "second-password", "Other");
        assertThat(restart.bootstrap()).isEqualTo(AdminBootstrap.Outcome.EXISTS);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM user_account WHERE email_normalized = 'boot-admin@example.test'
                    AND role = 'ADMIN' AND status = 'ACTIVE' AND display_name = 'Boot Admin' AND password_hash = ?
                """, Integer.class, hash)).isEqualTo(1);
    }

    @Test
    @Transactional
    void bootstrapLeavesExistingCustomerUntouched() {
        jdbc.update("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES ('taken@example.test', 'Customer', 'unused', 'CUSTOMER', 'ACTIVE')
                """);
        var bootstrap = new AdminBootstrap(jdbc, passwords, "taken@example.test", "admin-password", "Admin");
        assertThat(bootstrap.bootstrap()).isEqualTo(AdminBootstrap.Outcome.EXISTS);
        assertThat(jdbc.queryForObject("SELECT role FROM user_account WHERE email_normalized = 'taken@example.test'",
                String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void bootstrapIsSkippedWithoutEmailAndRejectsWeakPassword() {
        assertThat(new AdminBootstrap(jdbc, passwords, "", "", "Admin").bootstrap())
                .isEqualTo(AdminBootstrap.Outcome.SKIPPED);
        assertThatThrownBy(() -> new AdminBootstrap(jdbc, passwords, "weak@example.test", "short", "Admin").bootstrap())
                .isInstanceOf(IllegalStateException.class);
    }
}
