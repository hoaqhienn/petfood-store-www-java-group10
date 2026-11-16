package vn.edu.dhktpm19att.group10.petfood.account;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN from local configuration. Running it again never duplicates the account,
 * resets its password or changes an existing account with the same email.
 */
@Component
class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final String displayName;

    AdminBootstrap(JdbcTemplate jdbc, PasswordEncoder passwords,
            @Value("${app.admin.email:}") String email,
            @Value("${app.admin.password:}") String password,
            @Value("${app.admin.display-name:Quản trị viên}") String displayName) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.password = password;
        this.displayName = displayName.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    Outcome bootstrap() {
        if (email.isEmpty()) return Outcome.SKIPPED;
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 254
                || password.length() < 8 || password.length() > 64
                || displayName.isEmpty() || displayName.length() > 120) {
            throw new IllegalStateException("Cấu hình admin khởi tạo không hợp lệ.");
        }
        int created = jdbc.update("""
                INSERT INTO user_account(email_normalized, display_name, password_hash, role, status)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE')
                ON CONFLICT (email_normalized) DO NOTHING
                """, email, displayName, passwords.encode(password));
        if (created == 1) {
            log.info("Admin bootstrap created the configured administrator account.");
            return Outcome.CREATED;
        }
        log.info("Admin bootstrap skipped: an account with the configured email already exists.");
        return Outcome.EXISTS;
    }

    enum Outcome { SKIPPED, CREATED, EXISTS }
}
