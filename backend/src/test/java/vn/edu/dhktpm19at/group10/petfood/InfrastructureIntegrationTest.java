package vn.edu.dhktpm19at.group10.petfood;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
class InfrastructureIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SessionRepository<? extends Session> sessions;

    @Test
    void flywayEnablesVectorExtension() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void sessionSurvivesRedisRoundTrip() {
        verifySessionRoundTrip(sessions);
    }

    private <S extends Session> void verifySessionRoundTrip(SessionRepository<S> repository) {
        S session = repository.createSession();
        session.setAttribute("scaffold-check", "ok");
        try {
            repository.save(session);
            S restored = repository.findById(session.getId());
            assertThat(restored).isNotNull();
            assertThat((String) restored.getAttribute("scaffold-check")).isEqualTo("ok");
        } finally {
            repository.deleteById(session.getId());
        }
    }
}

