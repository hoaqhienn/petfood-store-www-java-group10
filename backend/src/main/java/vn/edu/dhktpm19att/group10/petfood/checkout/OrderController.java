package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.dhktpm19att.group10.petfood.cart.SessionCartStore;
import vn.edu.dhktpm19att.group10.petfood.config.RedisSessionFailureFilter;
import vn.edu.dhktpm19att.group10.petfood.tracking.Tracking;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {
    private final OrderPlacement orders;
    private final SessionCartStore carts;
    private final ObjectMapper json;
    private final Tracking tracking;

    OrderController(OrderPlacement orders, SessionCartStore carts, ObjectMapper json, Tracking tracking) {
        this.orders = orders;
        this.carts = carts;
        this.json = json;
        this.tracking = tracking;
    }

    @PostMapping
    ResponseEntity<?> place(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody OrderRequest body,
            Authentication authentication, HttpSession session, HttpServletRequest request) throws JsonProcessingException {
        if (!key.matches("[A-Za-z0-9._:-]{1,128}")) {
            return CheckoutPreviewController.error(400, "INVALID_REQUEST", "Idempotency-Key không hợp lệ.");
        }
        var result = orders.place(Long.parseLong(authentication.getName()), key, fingerprint(body), body, session);
        int status = result.replay() ? 200 : 201;
        // Spring Session may also fail when persisting the session at the end of the request.
        RedisSessionFailureFilter.preserveOrderResponse(request, status, result.order());
        // PostgreSQL has committed. A failed cart cleanup must not turn it into a failed order.
        try {
            carts.clearIfCurrent(session, body.cartToken(), body.cartRevision());
        } catch (DataAccessException exception) {
            // The same request can retry cleanup without creating a second order.
        }
        // Only the backend records a placed order, once, and only with analytics consent (BR25).
        if (!result.replay()) tracking.serverEvent(session, authentication, "order_placed", java.util.Map.of());
        return ResponseEntity.status(status).body(result.order());
    }

    private String fingerprint(OrderRequest body) throws JsonProcessingException {
        try {
            byte[] bytes = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @ExceptionHandler(OrderPlacement.Rejected.class)
    ResponseEntity<?> rejected(OrderPlacement.Rejected error) {
        return CheckoutPreviewController.error(error.status, error.code, error.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicateCart() {
        return CheckoutPreviewController.error(409, "CART_CHANGED", "Phiên bản giỏ này đã được đặt.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return CheckoutPreviewController.error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể xác nhận đơn.");
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid() {
        return CheckoutPreviewController.error(422, "INVALID_INPUT", "Thông tin đặt hàng không hợp lệ.");
    }

    record OrderRequest(@NotBlank @Size(max = 64) String cartToken, @NotNull @PositiveOrZero Long cartRevision,
            @NotBlank @Size(max = 64) String quoteToken, @NotNull @Pattern(regexp = "COD") String paymentMethod,
            @NotNull @Valid Recipient recipient) {}
    record Recipient(@NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[+0-9][0-9 ()-]{7,19}") String phone,
            @NotBlank @Size(max = 500) String address) {}
}
