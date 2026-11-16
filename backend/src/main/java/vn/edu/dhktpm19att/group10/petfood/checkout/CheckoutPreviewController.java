package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import vn.edu.dhktpm19att.group10.petfood.cart.SessionCartStore;
import vn.edu.dhktpm19att.group10.petfood.catalog.CatalogQueries;

@RestController
@RequestMapping("/api/v1/checkout")
class CheckoutPreviewController {
    private static final Duration QUOTE_TTL = Duration.ofMinutes(10);
    private final SessionCartStore carts;
    private final CatalogQueries catalog;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    CheckoutPreviewController(SessionCartStore carts, CatalogQueries catalog, JdbcTemplate jdbc,
            StringRedisTemplate redis, ObjectMapper json) {
        this.carts = carts;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.redis = redis;
        this.json = json;
    }

    @PostMapping("/preview")
    ResponseEntity<?> preview(@Valid @RequestBody PreviewRequest request, Authentication authentication,
            HttpSession session) throws JsonProcessingException {
        String actor = authentication.getName();
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_account WHERE id = ?
                    AND status = 'ACTIVE' AND role IN ('CUSTOMER', 'ADMIN'))
                """, Boolean.class, Long.parseLong(actor)));
        if (!active) return error(403, "ACCOUNT_LOCKED", "Tài khoản không thể đặt hàng.");

        var cart = carts.readCart(session);
        if (cart.revision() != request.cartRevision()) {
            return error(409, "CART_CHANGED", "Giỏ hàng đã thay đổi.");
        }
        if (cart.quantities().isEmpty()) return error(422, "CART_EMPTY", "Giỏ hàng đang trống.");

        var products = catalog.productsByIds(cart.quantities().keySet().stream().map(Long::valueOf).toList());
        var lines = new java.util.ArrayList<QuoteLine>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var entry : cart.quantities().entrySet()) {
            var product = products.get(entry.getKey());
            if (product == null) return error(422, "PRODUCT_UNAVAILABLE", "Giỏ có sản phẩm không còn bán.");
            if (product.availableStock() < entry.getValue()) {
                return error(422, "INSUFFICIENT_STOCK", "Giỏ có sản phẩm không đủ tồn kho.");
            }
            BigDecimal lineTotal = new BigDecimal(product.price()).multiply(BigDecimal.valueOf(entry.getValue()));
            lines.add(new QuoteLine(product.id(), product.sku(), product.name(),
                    entry.getValue(), product.price(), lineTotal.toPlainString()));
            subtotal = subtotal.add(lineTotal);
        }

        String quoteToken = UUID.randomUUID().toString();
        String amount = subtotal.toPlainString();
        var quote = new Quote(quoteToken, cart.cartToken(), cart.revision(), Instant.now().plus(QUOTE_TTL),
                lines, amount, "0", amount, "COD");
        redis.opsForValue().set("petfood:checkout:quote:" + quoteToken,
                json.writeValueAsString(new StoredQuote(actor, quote)), QUOTE_TTL);
        return ResponseEntity.ok(quote);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalidInput() {
        return error(422, "INVALID_INPUT", "Phiên bản giỏ hàng không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorBody> unavailable() {
        return error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể báo giá.");
    }

    static ResponseEntity<ErrorBody> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorBody(code, message, List.of(), UUID.randomUUID().toString()));
    }

    record PreviewRequest(@NotNull @PositiveOrZero Long cartRevision) {}
    record QuoteLine(String productId, String sku, String name, int quantity, String unitPrice, String lineTotal) {}
    record Quote(String quoteToken, String cartToken, long cartRevision, Instant expiresAt, List<QuoteLine> items,
            String subtotal, String shippingFee, String grandTotal, String paymentMethod) {}
    record StoredQuote(String customerId, Quote quote) {}
    record ErrorBody(String code, String message, List<Object> fieldErrors, String traceId) {}
}
