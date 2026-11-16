package vn.edu.dhktpm19att.group10.petfood.config;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Handles Redis connection failures even when Spring Session fails before MVC. */
@Component
@Order(SessionRepositoryFilter.DEFAULT_ORDER - 1)
public class RedisSessionFailureFilter extends OncePerRequestFilter {
    private static final String ORDER_RESPONSE = RedisSessionFailureFilter.class.getName() + ".orderResponse";
    private final ObjectMapper json;

    RedisSessionFailureFilter(ObjectMapper json) {
        this.json = json;
    }

    public static void preserveOrderResponse(HttpServletRequest request, int status, Object order) {
        request.setAttribute(ORDER_RESPONSE, new CommittedOrderResponse(status, order));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (RedisConnectionFailureException failure) {
            if (response.isCommitted()) return;
            response.resetBuffer();
            var order = (CommittedOrderResponse) request.getAttribute(ORDER_RESPONSE);
            byte[] body = json.writeValueAsBytes(order == null ? Map.of(
                    "code", "DEPENDENCY_UNAVAILABLE",
                    "message", "Phiên làm việc tạm thời chưa khả dụng. Vui lòng thử lại.") : order.body());
            response.setStatus(order == null ? HttpServletResponse.SC_SERVICE_UNAVAILABLE : order.status());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
        }
    }

    private record CommittedOrderResponse(int status, Object body) {}
}
