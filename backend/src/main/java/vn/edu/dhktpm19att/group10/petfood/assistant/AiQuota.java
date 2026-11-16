package vn.edu.dhktpm19att.group10.petfood.assistant;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Per-Session limit on AI-backed requests, so one visitor cannot spend the whole API budget. */
@Component
class AiQuota {
    private final StringRedisTemplate redis;
    private final int perMinute;

    AiQuota(StringRedisTemplate redis, @Value("${app.ai.quota-per-minute:10}") int perMinute) {
        this.redis = redis;
        this.perMinute = perMinute;
    }

    /** Counts one request; returns false when this Session is over its limit for the current minute. */
    boolean tryAcquire(HttpSession session) {
        String key = "petfood:ai:quota:" + session.getId() + ":" + Instant.now().getEpochSecond() / 60;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(2));
        return count != null && count <= perMinute;
    }
}
