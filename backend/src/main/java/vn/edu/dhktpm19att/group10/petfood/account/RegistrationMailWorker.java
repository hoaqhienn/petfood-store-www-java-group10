package vn.edu.dhktpm19att.group10.petfood.account;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@EnableScheduling
class RegistrationMailWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final JavaMailSender mail;
    private final String from;

    RegistrationMailWorker(JdbcTemplate jdbc, TransactionTemplate transactions, JavaMailSender mail,
            @Value("${app.mail.from}") String from) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mail = mail;
        this.from = from;
    }

    @Scheduled(fixedDelay = 5000)
    void sendDueMail() {
        transactions.executeWithoutResult(transaction -> {
            var events = jdbc.query("""
                    SELECT o.id, o.attempts, a.email_normalized, o.payload->>'orderCode'
                    FROM notification_outbox o LEFT JOIN user_account a ON a.id = o.aggregate_id
                    WHERE o.type IN ('REGISTRATION', 'ORDER_PLACED')
                        AND o.status = 'PENDING' AND o.next_attempt_at <= now()
                    ORDER BY o.next_attempt_at, o.id LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                    """, (rs, row) -> new Event(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getString(4)));
            if (!events.isEmpty()) deliver(events.get(0));
        });
    }

    private void deliver(Event event) {
        if (event.email() == null) {
            jdbc.update("UPDATE notification_outbox SET status = 'CANCELLED' WHERE id = ?", event.id());
            return;
        }
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject("PetFood | Đăng ký thành công");
        message.setText("Tài khoản PetFood của bạn đã được tạo. Bạn có thể đăng nhập bằng email đã đăng ký.");
        if (event.orderCode() != null) {
            message.setSubject("PetFood | Xác nhận đơn hàng COD");
            message.setText("PetFood đã nhận đơn " + event.orderCode() + ". Bạn sẽ thanh toán khi nhận hàng.");
        }
        try {
            mail.send(message);
        } catch (MailException exception) {
            int attempts = event.attempts() + 1;
            jdbc.update("""
                    UPDATE notification_outbox SET attempts = ?, status = ?,
                    next_attempt_at = now() + (? * interval '1 second') WHERE id = ?
                    """, attempts, attempts >= 3 ? "FAILED" : "PENDING", 30 * (1 << (attempts - 1)), event.id());
            return;
        }
        jdbc.update("UPDATE notification_outbox SET status = 'SENT', sent_at = now(), attempts = attempts + 1 WHERE id = ?",
                event.id());
    }

    private record Event(UUID id, int attempts, String email, String orderCode) {}
}
