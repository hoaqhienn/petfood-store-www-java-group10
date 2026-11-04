package vn.edu.dhktpm19att.group10.petfood.cart;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class SessionCartStore {
    private static final DefaultRedisScript<String> READ = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
              return value
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return ARGV[1]
            """, String.class);
    private static final DefaultRedisScript<Long> REPLACE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    SessionCartStore(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    Snapshot read(HttpSession session) {
        var empty = new Cart(UUID.randomUUID().toString(), 0, Map.of());
        String value = redis.execute(READ, List.of(key(session)), encode(empty), ttl(session));
        try {
            return new Snapshot(value, json.readValue(value, Cart.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid Redis cart data", exception);
        }
    }

    boolean replace(HttpSession session, Snapshot previous, Cart next) {
        return Long.valueOf(1).equals(redis.execute(REPLACE, List.of(key(session)),
                previous.raw(), encode(next), ttl(session)));
    }

    private String encode(Cart cart) {
        try {
            return json.writeValueAsString(cart);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode cart data", exception);
        }
    }

    private static String key(HttpSession session) {
        return "petfood:cart:" + session.getId();
    }

    private static String ttl(HttpSession session) {
        return Integer.toString(session.getMaxInactiveInterval());
    }

    record Cart(String cartToken, long revision, Map<String, Integer> quantities) {}
    record Snapshot(String raw, Cart cart) {}
}
