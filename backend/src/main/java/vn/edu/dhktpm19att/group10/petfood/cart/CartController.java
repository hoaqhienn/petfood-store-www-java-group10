package vn.edu.dhktpm19att.group10.petfood.cart;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.dhktpm19att.group10.petfood.catalog.CatalogQueries;

@RestController
@RequestMapping("/api/v1")
class CartController {
    private final SessionCartStore store;
    private final CatalogQueries catalog;

    CartController(SessionCartStore store, CatalogQueries catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    @GetMapping("/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/cart")
    CartView get(HttpSession session) {
        return view(store.read(session).cart());
    }

    @PostMapping("/cart/items")
    CartView add(HttpSession session, @RequestBody AddItem request) {
        return change(session, request.productId(), quantity(request.quantity()), revision(request.expectedRevision()), true);
    }

    @PatchMapping("/cart/items/{productId}")
    CartView update(HttpSession session, @PathVariable String productId, @RequestBody UpdateItem request) {
        return change(session, productId, quantity(request.quantity()), revision(request.expectedRevision()), false);
    }

    @DeleteMapping("/cart/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(HttpSession session, @PathVariable String productId, @RequestParam long expectedRevision) {
        productId(productId);
        var previous = read(session, expectedRevision);
        var items = new LinkedHashMap<>(previous.cart().quantities());
        if (items.remove(productId) == null) throw missingItem();
        save(session, previous, next(previous.cart(), items));
    }

    private CartView change(HttpSession session, String id, int quantity, long expectedRevision, boolean add) {
        productId(id);
        var previous = read(session, expectedRevision);
        var items = new LinkedHashMap<>(previous.cart().quantities());
        if (!add && !items.containsKey(id)) throw missingItem();
        if (!items.containsKey(id) && items.size() >= 50) {
            throw new CartError(422, "CART_LIMIT", "Giỏ hàng đã có 50 sản phẩm khác nhau.");
        }
        int total = add ? items.getOrDefault(id, 0) + quantity : quantity;
        if (total > 99) throw invalidQuantity();
        items.put(id, total);
        var next = next(previous.cart(), items);
        CartView response = view(next);
        var item = response.items().stream().filter(line -> line.productId().equals(id)).findFirst().orElseThrow();
        if ("PRODUCT_UNAVAILABLE".equals(item.availabilityError())) {
            throw new CartError(422, "PRODUCT_UNAVAILABLE", "Sản phẩm không còn bán.");
        }
        if ("INSUFFICIENT_STOCK".equals(item.availabilityError())) {
            throw new CartError(422, "INSUFFICIENT_STOCK", "Số lượng vượt quá tồn kho hiện tại.");
        }
        save(session, previous, next);
        return response;
    }

    private SessionCartStore.Snapshot read(HttpSession session, long expectedRevision) {
        if (expectedRevision < 0) throw invalidRequest();
        var snapshot = store.read(session);
        if (snapshot.cart().revision() != expectedRevision) throw changed();
        return snapshot;
    }

    private void save(HttpSession session, SessionCartStore.Snapshot previous, SessionCartStore.Cart next) {
        if (!store.replace(session, previous, next)) throw changed();
    }

    private static SessionCartStore.Cart next(SessionCartStore.Cart cart, Map<String, Integer> items) {
        return new SessionCartStore.Cart(cart.cartToken(), cart.revision() + 1, items);
    }

    private CartView view(SessionCartStore.Cart cart) {
        var products = catalog.productsByIds(cart.quantities().keySet().stream().map(Long::valueOf).toList());
        var items = cart.quantities().entrySet().stream().map(entry -> {
            var product = products.get(entry.getKey());
            int quantity = entry.getValue();
            if (product == null) {
                return new Item(entry.getKey(), quantity, null, null, null, "PRODUCT_UNAVAILABLE");
            }
            String availability = quantity > product.availableStock() ? "INSUFFICIENT_STOCK" : null;
            return new Item(entry.getKey(), quantity, product.name(), product.price(), product.availableStock(), availability);
        }).toList();
        BigDecimal subtotal = items.stream().filter(item -> item.unitPrice() != null)
                .map(item -> new BigDecimal(item.unitPrice()).multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(cart.cartToken(), cart.revision(), items, subtotal.toPlainString());
    }

    private static long productId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalidRequest();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalidRequest();
        }
    }

    private static int quantity(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < 1 || value.intValue() > 99) throw invalidQuantity();
        return value.intValue();
    }

    private static long revision(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw invalidRequest();
        return value.longValue();
    }

    private static CartError invalidQuantity() {
        return new CartError(422, "INVALID_QUANTITY", "Số lượng phải là số nguyên từ 1 đến 99.");
    }

    private static CartError invalidRequest() {
        return new CartError(400, "INVALID_REQUEST", "Thông tin giỏ hàng không hợp lệ.");
    }

    private static CartError missingItem() {
        return new CartError(404, "CART_ITEM_NOT_FOUND", "Sản phẩm không có trong giỏ.");
    }

    private static CartError changed() {
        return new CartError(409, "CART_CHANGED", "Giỏ hàng đã thay đổi. Hãy tải lại giỏ.");
    }

    @ExceptionHandler(CartError.class)
    ResponseEntity<?> cartError(CartError error) {
        return ResponseEntity.status(error.status).body(Map.of("code", error.code, "message", error.getMessage(),
                "fieldErrors", List.of(), "traceId", UUID.randomUUID().toString()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<?> malformed() {
        return cartError(invalidRequest());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return cartError(new CartError(503, "DEPENDENCY_UNAVAILABLE", "Giỏ hàng tạm thời chưa khả dụng."));
    }

    record AddItem(String productId, JsonNode quantity, JsonNode expectedRevision) {}
    record UpdateItem(JsonNode quantity, JsonNode expectedRevision) {}
    record Item(String productId, int quantity, String name, String unitPrice, Integer availableStock,
            String availabilityError) {}
    record CartView(String cartToken, long revision, List<Item> items, String subtotal) {}

    private static class CartError extends RuntimeException {
        private final int status;
        private final String code;

        CartError(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
