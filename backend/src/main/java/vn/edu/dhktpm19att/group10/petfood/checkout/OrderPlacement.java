package vn.edu.dhktpm19att.group10.petfood.checkout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.dhktpm19att.group10.petfood.cart.SessionCartStore;

@Service
class OrderPlacement {
    private final NamedParameterJdbcTemplate db;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final SessionCartStore carts;

    OrderPlacement(NamedParameterJdbcTemplate db, StringRedisTemplate redis, ObjectMapper json,
            SessionCartStore carts) {
        this.db = db;
        this.redis = redis;
        this.json = json;
        this.carts = carts;
    }

    @Transactional
    public Result place(long actor, String key, String fingerprint, OrderController.OrderRequest body,
            HttpSession session) {
        // Serialize orders for one customer, including requests using the same key.
        var accounts = db.query("""
                SELECT status, role FROM user_account WHERE id = :actor FOR UPDATE
                """, Map.of("actor", actor), (rs, row) ->
                "ACTIVE".equals(rs.getString("status")) && List.of("CUSTOMER", "ADMIN").contains(rs.getString("role")));
        if (accounts.isEmpty() || !accounts.get(0)) throw reject(403, "ACCOUNT_LOCKED", "Tài khoản không thể đặt hàng.");

        var replay = db.query("""
                SELECT o.id, o.order_code, o.status, o.payment_status, o.grand_total, r.fingerprint
                FROM checkout_request r JOIN customer_order o ON o.id = r.order_id
                WHERE r.customer_id = :actor AND r.idempotency_key = :key
                """, Map.of("actor", actor, "key", key), (rs, row) -> {
                    if (!fingerprint.equals(rs.getString("fingerprint"))) {
                        throw reject(409, "IDEMPOTENCY_CONFLICT", "Khóa đã dùng cho yêu cầu khác.");
                    }
                    return new Order(rs.getString("id"), rs.getString("order_code"), rs.getString("status"),
                            "COD", rs.getString("payment_status"), rs.getBigDecimal("grand_total").toPlainString());
                });
        if (!replay.isEmpty()) return new Result(replay.get(0), true);

        var quote = readQuote(actor, body);
        var cart = carts.readCart(session);
        if (!cart.cartToken().equals(body.cartToken()) || cart.revision() != body.cartRevision()) {
            throw reject(409, "CART_CHANGED", "Giỏ hàng đã thay đổi.");
        }
        var ids = quote.items().stream().map(line -> Long.valueOf(line.productId())).toList();
        var categories = db.queryForList("""
                SELECT DISTINCT category_id FROM product WHERE id IN (:ids)
                """, Map.of("ids", ids), Long.class);
        if (categories.isEmpty()) throw unavailableProduct();
        db.queryForList("SELECT id FROM category WHERE id IN (:ids) ORDER BY id FOR UPDATE",
                Map.of("ids", categories), Long.class);
        db.queryForList("SELECT id FROM product WHERE id IN (:ids) ORDER BY id FOR UPDATE",
                Map.of("ids", ids), Long.class);

        for (var line : quote.items()) {
            var products = db.query("""
                    SELECT p.category_id, p.price, p.available_stock
                    FROM product p JOIN category c ON c.id = p.category_id
                    WHERE p.id = :id AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'
                    """, Map.of("id", Long.valueOf(line.productId())), (rs, row) ->
                    new Stock(rs.getLong("category_id"), rs.getBigDecimal("price"), rs.getInt("available_stock")));
            if (products.isEmpty()) throw unavailableProduct();
            var current = products.get(0);
            if (!categories.contains(current.categoryId())) {
                throw reject(409, "PRODUCT_UNAVAILABLE", "Danh mục đã đổi. Hãy xem lại báo giá.");
            }
            if (current.price().compareTo(new BigDecimal(line.unitPrice())) != 0) {
                throw reject(409, "PRICE_CHANGED", "Giá sản phẩm đã thay đổi.");
            }
            if (current.quantity() < line.quantity()) {
                throw reject(422, "INSUFFICIENT_STOCK", "Sản phẩm không đủ tồn kho.");
            }
        }
        if (!quote.expiresAt().isAfter(Instant.now())) throw expiredQuote();
        return insert(actor, key, fingerprint, body, quote);
    }

    private CheckoutPreviewController.Quote readQuote(long actor, OrderController.OrderRequest body) {
        String value = redis.opsForValue().get("petfood:checkout:quote:" + body.quoteToken());
        if (value == null) throw expiredQuote();
        try {
            var stored = json.readValue(value, CheckoutPreviewController.StoredQuote.class);
            var quote = stored.quote();
            if (!Long.toString(actor).equals(stored.customerId()) || !quote.expiresAt().isAfter(Instant.now())) {
                throw expiredQuote();
            }
            if (!quote.cartToken().equals(body.cartToken()) || quote.cartRevision() != body.cartRevision()) {
                throw reject(409, "CART_CHANGED", "Báo giá không khớp giỏ hàng.");
            }
            return quote;
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Invalid checkout quote data", invalid);
        }
    }

    private Result insert(long actor, String key, String fingerprint, OrderController.OrderRequest body,
            CheckoutPreviewController.Quote quote) {
        UUID id = UUID.randomUUID();
        String code = "PF-" + id;
        var recipient = body.recipient();
        db.update("""
                INSERT INTO customer_order(id, order_code, customer_id, recipient_name, phone, address_text,
                    subtotal, shipping_fee, grand_total)
                VALUES (:id, :code, :actor, :name, :phone, :address, :subtotal, :shipping, :total)
                """, Map.of("id", id, "code", code, "actor", actor, "name", recipient.name().strip(),
                "phone", recipient.phone(), "address", recipient.address().strip(),
                "subtotal", new BigDecimal(quote.subtotal()), "shipping", new BigDecimal(quote.shippingFee()),
                "total", new BigDecimal(quote.grandTotal())));
        db.update("""
                INSERT INTO checkout_request(customer_id, idempotency_key, fingerprint, cart_token, cart_revision, order_id)
                VALUES (:actor, :key, :fingerprint, :token, :revision, :id)
                """, Map.of("actor", actor, "key", key, "fingerprint", fingerprint, "token", body.cartToken(),
                "revision", body.cartRevision(), "id", id));
        for (var line : quote.items()) {
            var values = Map.of("order", id, "product", Long.valueOf(line.productId()), "sku", line.sku(),
                    "name", line.name(), "price", new BigDecimal(line.unitPrice()), "quantity", line.quantity(),
                    "total", new BigDecimal(line.lineTotal()), "actor", actor);
            db.update("""
                    INSERT INTO order_item(order_id, product_id, sku_snapshot, name_snapshot, unit_price, quantity, line_total)
                    VALUES (:order, :product, :sku, :name, :price, :quantity, :total)
                    """, values);
            db.update("UPDATE product SET available_stock = available_stock - :quantity, version = version + 1 WHERE id = :product", values);
            db.update("""
                    INSERT INTO inventory_movement(product_id, sku_snapshot, order_id, actor_id, delta, reason)
                    VALUES (:product, :sku, :order, :actor, -:quantity, 'ORDER_PLACED')
                    """, values);
        }
        db.update("""
                INSERT INTO order_history(order_id, actor_id, action, to_status)
                VALUES (:id, :actor, 'ORDER_PLACED', 'PENDING')
                """, Map.of("id", id, "actor", actor));
        db.update("""
                INSERT INTO notification_outbox(id, type, aggregate_id, payload)
                VALUES (:id, 'ORDER_PLACED', :actor, jsonb_build_object('orderCode', CAST(:code AS text)))
                """, Map.of("id", UUID.randomUUID(), "actor", actor, "code", code));
        return new Result(new Order(id.toString(), code, "PENDING", "COD", "UNPAID", quote.grandTotal()), false);
    }

    private static Rejected unavailableProduct() {
        return reject(422, "PRODUCT_UNAVAILABLE", "Sản phẩm không còn bán.");
    }

    private static Rejected expiredQuote() {
        return reject(409, "QUOTE_EXPIRED", "Báo giá đã hết hiệu lực.");
    }

    private static Rejected reject(int status, String code, String message) {
        return new Rejected(status, code, message);
    }

    record Result(Order order, boolean replay) {}
    record Order(String id, String orderCode, String status, String paymentMethod, String paymentStatus, String grandTotal) {}
    private record Stock(long categoryId, BigDecimal price, int quantity) {}

    static class Rejected extends RuntimeException {
        final int status;
        final String code;

        Rejected(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
