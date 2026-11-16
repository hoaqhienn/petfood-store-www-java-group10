package vn.edu.dhktpm19att.group10.petfood.tracking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Raw events and impressions are kept 90 days (BR26). Orders are business records and are never touched. */
@Component
class TrackingRetention {
    private final JdbcTemplate db;

    TrackingRetention(JdbcTemplate db) {
        this.db = db;
    }

    @Scheduled(cron = "${app.tracking.retention-cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    int purge() {
        return db.update("DELETE FROM behavior_event WHERE server_received_at < now() - interval '90 days'")
                + db.update("DELETE FROM recommendation_impression WHERE created_at < now() - interval '90 days'");
    }
}
