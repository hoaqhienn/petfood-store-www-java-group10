package vn.edu.dhktpm19att.group10.petfood.account;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class Accounts {
    private static final String COLUMNS = "id, email_normalized, display_name, phone, role, status, version, password_hash";
    private static final RowMapper<Account> MAPPER = (rs, row) -> new Account(rs.getLong("id"),
            rs.getString("email_normalized"), rs.getString("display_name"), rs.getString("phone"),
            rs.getString("role"), rs.getString("status"), rs.getLong("version"), rs.getString("password_hash"));
    private final JdbcTemplate jdbc;

    Accounts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Account register(String email, String name, String hash) {
        var account = jdbc.query("""
                INSERT INTO user_account(email_normalized, display_name, password_hash, role, status)
                VALUES (?, ?, ?, 'CUSTOMER', 'ACTIVE')
                ON CONFLICT (email_normalized) DO NOTHING RETURNING
                """ + COLUMNS, MAPPER, email, name, hash).stream().findFirst().orElse(null);
        if (account != null) {
            jdbc.update("INSERT INTO notification_outbox(id, type, aggregate_id) VALUES (?, 'REGISTRATION', ?)",
                    UUID.randomUUID(), account.id());
        }
        return account;
    }

    Account byEmail(String email) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_account WHERE email_normalized = ?",
                MAPPER, email).stream().findFirst().orElse(null);
    }

    Account byId(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_account WHERE id = ?",
                MAPPER, id).stream().findFirst().orElse(null);
    }

    record Account(long id, String email, String displayName, String phone, String role, String status,
            long version, String passwordHash) {
        Summary summary() { return new Summary(Long.toString(id), email, displayName, phone, role, status, version); }
    }

    record Summary(String id, String email, String displayName, String phone, String role, String status,
            long version) {}
}
