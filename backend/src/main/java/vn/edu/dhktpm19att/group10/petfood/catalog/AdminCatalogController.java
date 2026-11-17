package vn.edu.dhktpm19att.group10.petfood.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Admin catalog management (F07, BR01-BR04). Stock changes only through signed adjustments with an audit row;
 * product and category locks follow the checkout order: categories by ascending id, then the SKU.
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminCatalogController {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> SPECIES = Set.of("DOG", "CAT", "BOTH");
    private static final Set<String> LIFE_STAGES = Set.of("JUNIOR", "ADULT", "SENIOR", "ALL");
    private static final Set<String> CATEGORY_FIELDS = Set.of("name", "slug", "status", "version");
    private static final Set<String> PRODUCT_FIELDS = Set.of("categoryId", "sku", "name", "description", "species",
            "lifeStage", "brand", "weightGrams", "price", "imageUrl", "status", "version");
    private static final Set<String> CONTENT_FIELDS = Set.of("name", "description", "species", "lifeStage", "brand");
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999999999");

    private static final String CATEGORY_SELECT = """
            SELECT c.id, c.name, c.slug, c.status, c.version,
                   (SELECT count(*) FROM product p WHERE p.category_id = c.id) AS product_count
            FROM category c
            """;
    private static final RowMapper<Category> CATEGORY = (rs, row) -> new Category(rs.getString("id"),
            rs.getString("name"), rs.getString("slug"), rs.getString("status"), rs.getLong("version"),
            rs.getLong("product_count"));
    private static final String PRODUCT_SELECT = """
            SELECT p.id, p.sku, p.name, p.description, p.species, p.life_stage, p.brand, p.weight_grams, p.price,
                   p.available_stock, p.image_url, p.status, p.content_version, p.version,
                   c.id AS category_id, c.name AS category_name, c.status AS category_status
            FROM product p JOIN category c ON c.id = p.category_id
            """;
    private static final RowMapper<Product> PRODUCT = (rs, row) -> new Product(rs.getString("id"),
            rs.getString("sku"), rs.getString("name"), rs.getString("description"), rs.getString("species"),
            rs.getString("life_stage"), rs.getString("brand"), rs.getInt("weight_grams"),
            rs.getBigDecimal("price").toPlainString(), rs.getInt("available_stock"), rs.getString("image_url"),
            rs.getString("status"), rs.getLong("content_version"), rs.getLong("version"),
            rs.getString("category_id"), rs.getString("category_name"), rs.getString("category_status"));

    private final JdbcTemplate db;

    AdminCatalogController(JdbcTemplate db) {
        this.db = db;
    }

    // ---- Categories ----

    @GetMapping("/categories")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> categories(Authentication auth, @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        activeAdmin(auth);
        checkPage(page, size, q);
        status = blankToNull(status);
        if (status != null && !STATUSES.contains(status)) throw invalidQuery();
        var where = new StringBuilder(" WHERE TRUE");
        var args = new ArrayList<Object>();
        if (!q.isBlank()) {
            where.append(" AND (strpos(lower(c.name), lower(?)) > 0 OR strpos(c.slug, lower(?)) > 0)");
            args.add(q.strip());
            args.add(q.strip());
        }
        if (status != null) {
            where.append(" AND c.status = ?");
            args.add(status);
        }
        long total = db.queryForObject("SELECT count(*) FROM category c" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var items = db.query(CATEGORY_SELECT + where + " ORDER BY c.name, c.id LIMIT ? OFFSET ?", CATEGORY,
                args.toArray());
        return ResponseEntity.ok(new Page<>(items, page, size, total, pages(total, size)));
    }

    @GetMapping("/categories/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> category(Authentication auth, @PathVariable String id) {
        activeAdmin(auth);
        return ResponseEntity.ok(findCategory(id(id, "CATEGORY_NOT_FOUND")));
    }

    @PostMapping("/categories")
    @Transactional
    public ResponseEntity<?> createCategory(Authentication auth, @RequestBody JsonNode body) {
        activeAdmin(auth);
        whitelist(body, CATEGORY_FIELDS);
        if (body.has("version")) throw invalid("Không gửi version khi tạo mới.");
        String name = text(body, "name", 1, 120, true);
        String slug = slug(body);
        String status = choice(body, "status", STATUSES, true);
        unique("SELECT EXISTS (SELECT 1 FROM category WHERE slug = ?)", slug);
        Long id = db.queryForObject("""
                INSERT INTO category (name, slug, status) VALUES (?, ?, ?) RETURNING id
                """, Long.class, name, slug, status);
        return ResponseEntity.status(HttpStatus.CREATED).body(findCategory(id));
    }

    @PatchMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<?> updateCategory(Authentication auth, @PathVariable String id, @RequestBody JsonNode body) {
        activeAdmin(auth);
        long categoryId = id(id, "CATEGORY_NOT_FOUND");
        whitelist(body, CATEGORY_FIELDS);
        long version = version(body);
        var current = db.query("SELECT name, slug, status, version FROM category WHERE id = ? FOR UPDATE",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)},
                categoryId);
        if (current.isEmpty()) throw missing("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục.");
        var row = current.get(0);
        if (Long.parseLong(row[3]) != version) throw versionConflict();
        String name = body.has("name") ? text(body, "name", 1, 120, true) : row[0];
        String slug = body.has("slug") ? slug(body) : row[1];
        String status = body.has("status") ? choice(body, "status", STATUSES, true) : row[2];
        unique("SELECT EXISTS (SELECT 1 FROM category WHERE slug = ? AND id <> ?)", slug, categoryId);
        db.update("UPDATE category SET name = ?, slug = ?, status = ?, version = version + 1 WHERE id = ?",
                name, slug, status, categoryId);
        return ResponseEntity.ok(findCategory(categoryId));
    }

    @DeleteMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<?> deleteCategory(Authentication auth, @PathVariable String id) {
        activeAdmin(auth);
        long categoryId = id(id, "CATEGORY_NOT_FOUND");
        var locked = db.queryForList("SELECT id FROM category WHERE id = ? FOR UPDATE", Long.class, categoryId);
        if (locked.isEmpty()) throw missing("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục.");
        long products = db.queryForObject("SELECT count(*) FROM product WHERE category_id = ?", Long.class, categoryId);
        if (products > 0) {
            throw new Rejected(409, "CATEGORY_NOT_EMPTY", "Danh mục còn sản phẩm (kể cả sản phẩm ẩn) nên chưa thể xóa.");
        }
        db.update("DELETE FROM category WHERE id = ?", categoryId);
        return ResponseEntity.noContent().build();
    }

    // ---- Products ----

    @GetMapping("/products")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<?> products(Authentication auth, @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        activeAdmin(auth);
        checkPage(page, size, q);
        status = blankToNull(status);
        if ((status != null && !STATUSES.contains(status)) || (categoryId != null && categoryId < 1)) {
            throw invalidQuery();
        }
        var where = new StringBuilder(" WHERE TRUE");
        var args = new ArrayList<Object>();
        if (!q.isBlank()) {
            where.append(" AND (strpos(lower(p.name), lower(?)) > 0 OR strpos(lower(p.sku), lower(?)) > 0)");
            args.add(q.strip());
            args.add(q.strip());
        }
        if (categoryId != null) {
            where.append(" AND p.category_id = ?");
            args.add(categoryId);
        }
        if (status != null) {
            where.append(" AND p.status = ?");
            args.add(status);
        }
        long total = db.queryForObject("SELECT count(*) FROM product p" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var items = db.query(PRODUCT_SELECT + where + " ORDER BY p.name, p.id LIMIT ? OFFSET ?", PRODUCT,
                args.toArray());
        return ResponseEntity.ok(new Page<>(items, page, size, total, pages(total, size)));
    }

    @GetMapping("/products/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> product(Authentication auth, @PathVariable String id) {
        activeAdmin(auth);
        return ResponseEntity.ok(findProduct(id(id, "PRODUCT_NOT_FOUND")));
    }

    @PostMapping("/products")
    @Transactional
    public ResponseEntity<?> createProduct(Authentication auth, @RequestBody JsonNode body) {
        long admin = activeAdmin(auth);
        var allowed = new java.util.HashSet<>(PRODUCT_FIELDS);
        allowed.remove("version");
        allowed.add("initialStock");
        whitelist(body, allowed);
        long categoryId = positiveLong(body, "categoryId");
        var fields = productFields(body, null);
        int initialStock = body.has("initialStock") ? integer(body, "initialStock", 0, 1_000_000) : 0;
        if (db.queryForList("SELECT id FROM category WHERE id = ? FOR UPDATE", Long.class, categoryId).isEmpty()) {
            throw invalid("Danh mục không tồn tại.");
        }
        unique("SELECT EXISTS (SELECT 1 FROM product WHERE sku = ?)", fields.sku());
        Long id = db.queryForObject("""
                INSERT INTO product (category_id, sku, name, description, species, life_stage, brand, weight_grams,
                                     price, available_stock, image_url, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, categoryId, fields.sku(), fields.name(), fields.description(), fields.species(),
                fields.lifeStage(), fields.brand(), fields.weightGrams(), fields.price(), initialStock,
                fields.imageUrl(), fields.status());
        if (initialStock > 0) movement(id, fields.sku(), admin, initialStock, "Tồn ban đầu");
        return ResponseEntity.status(HttpStatus.CREATED).body(findProduct(id));
    }

    @PatchMapping("/products/{id}")
    @Transactional
    public ResponseEntity<?> updateProduct(Authentication auth, @PathVariable String id, @RequestBody JsonNode body) {
        activeAdmin(auth);
        long productId = id(id, "PRODUCT_NOT_FOUND");
        whitelist(body, PRODUCT_FIELDS);
        long version = version(body);
        Long target = body.has("categoryId") ? positiveLong(body, "categoryId") : null;
        var current = lockProduct(productId, target);
        if (current.version() != version) throw versionConflict();
        if (target != null && db.queryForList("SELECT id FROM category WHERE id = ?", Long.class, target).isEmpty()) {
            throw invalid("Danh mục không tồn tại.");
        }
        var fields = productFields(body, current);
        unique("SELECT EXISTS (SELECT 1 FROM product WHERE sku = ? AND id <> ?)", fields.sku(), productId);
        boolean content = CONTENT_FIELDS.stream().anyMatch(body::has);
        db.update("""
                UPDATE product SET category_id = ?, sku = ?, name = ?, description = ?, species = ?, life_stage = ?,
                    brand = ?, weight_grams = ?, price = ?, image_url = ?, status = ?,
                    content_version = content_version + ?, version = version + 1
                WHERE id = ?
                """, target == null ? current.categoryId() : target, fields.sku(), fields.name(), fields.description(),
                fields.species(), fields.lifeStage(), fields.brand(), fields.weightGrams(), fields.price(),
                fields.imageUrl(), fields.status(), content ? 1 : 0, productId);
        return ResponseEntity.ok(findProduct(productId));
    }

    @PostMapping("/products/{id}/stock-adjustments")
    @Transactional
    public ResponseEntity<?> adjustStock(Authentication auth, @PathVariable String id, @RequestBody JsonNode body) {
        long admin = activeAdmin(auth);
        long productId = id(id, "PRODUCT_NOT_FOUND");
        whitelist(body, Set.of("delta", "reason", "version"));
        int delta = integer(body, "delta", -1_000_000, 1_000_000);
        if (delta == 0) throw invalid("Số lượng điều chỉnh phải khác 0.");
        String reason = text(body, "reason", 1, 500, true);
        long version = version(body);
        var current = lockProduct(productId, null);
        // Checkout also bumps the product version, so a stale form cannot overwrite stock sold meanwhile (AC30).
        if (current.version() != version) throw versionConflict();
        if ((long) current.stock() + delta < 0) {
            throw new Rejected(422, "INVALID_STOCK", "Tồn kho sau điều chỉnh không được âm.");
        }
        db.update("UPDATE product SET available_stock = available_stock + ?, version = version + 1 WHERE id = ?",
                delta, productId);
        movement(productId, current.sku(), admin, delta, reason);
        return ResponseEntity.ok(findProduct(productId));
    }

    @DeleteMapping("/products/{id}")
    @Transactional
    public ResponseEntity<?> deleteProduct(Authentication auth, @PathVariable String id) {
        activeAdmin(auth);
        long productId = id(id, "PRODUCT_NOT_FOUND");
        lockProduct(productId, null);
        long lines = db.queryForObject("SELECT count(*) FROM order_item WHERE product_id = ?", Long.class, productId);
        if (lines > 0) {
            throw new Rejected(409, "PRODUCT_REFERENCED", "Sản phẩm đã có trong đơn hàng (kể cả đơn hủy). Hãy ẩn thay vì xóa.");
        }
        // inventory_movement keeps its sku_snapshot; its product_id becomes NULL through the FK.
        db.update("DELETE FROM product WHERE id = ?", productId);
        return ResponseEntity.noContent().build();
    }

    // ---- Helpers ----

    private Current lockProduct(long productId, Long targetCategory) {
        var categories = db.queryForList("SELECT category_id FROM product WHERE id = ?", Long.class, productId);
        if (categories.isEmpty()) throw missing("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm.");
        var lockIds = new java.util.TreeSet<Long>(categories);
        if (targetCategory != null) lockIds.add(targetCategory);
        db.queryForList("SELECT id FROM category WHERE id IN (" + String.join(",", lockIds.stream().map(x -> "?").toList())
                + ") ORDER BY id FOR UPDATE", Long.class, lockIds.toArray());
        var rows = db.query("""
                SELECT category_id, sku, name, description, species, life_stage, brand, weight_grams, price,
                       image_url, status, available_stock, version
                FROM product WHERE id = ? FOR UPDATE
                """, (rs, row) -> new Current(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8), rs.getBigDecimal(9),
                rs.getString(10), rs.getString(11), rs.getInt(12), rs.getLong(13)), productId);
        if (rows.isEmpty()) throw missing("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm.");
        var current = rows.get(0);
        if (!lockIds.contains(current.categoryId())) {
            throw new Rejected(409, "VERSION_CONFLICT", "Sản phẩm vừa đổi danh mục. Hãy tải lại.");
        }
        return current;
    }

    private Fields productFields(JsonNode body, Current current) {
        boolean create = current == null;
        String sku = body.has("sku") || create ? sku(body) : current.sku();
        String name = body.has("name") || create ? text(body, "name", 1, 200, true) : current.name();
        String description = body.has("description") || create
                ? text(body, "description", 0, 5000, true) : current.description();
        String species = body.has("species") || create ? choice(body, "species", SPECIES, true) : current.species();
        String lifeStage = body.has("lifeStage") || create
                ? choice(body, "lifeStage", LIFE_STAGES, true) : current.lifeStage();
        String brand = body.has("brand") || create ? text(body, "brand", 1, 120, true) : current.brand();
        int weight = body.has("weightGrams") || create
                ? integer(body, "weightGrams", 1, 1_000_000) : current.weightGrams();
        BigDecimal price = body.has("price") || create ? price(body) : current.price();
        String imageUrl = body.has("imageUrl") || create ? imageUrl(body) : current.imageUrl();
        String status = body.has("status") || create ? choice(body, "status", STATUSES, true) : current.status();
        return new Fields(sku, name, description, species, lifeStage, brand, weight, price, imageUrl, status);
    }

    /** Checked before writing so a duplicate does not abort the transaction; the UNIQUE index covers races. */
    private void unique(String sql, Object... args) {
        if (Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class, args))) {
            throw new Rejected(409, "DUPLICATE_VALUE", "SKU hoặc slug đã tồn tại.");
        }
    }

    private void movement(long productId, String sku, long admin, int delta, String reason) {
        db.update("""
                INSERT INTO inventory_movement(product_id, sku_snapshot, actor_id, delta, reason)
                VALUES (?, ?, ?, ?, ?)
                """, productId, sku, admin, delta, "ADMIN_ADJUSTMENT: " + reason);
    }

    private Category findCategory(long id) {
        var rows = db.query(CATEGORY_SELECT + " WHERE c.id = ?", CATEGORY, id);
        if (rows.isEmpty()) throw missing("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục.");
        return rows.get(0);
    }

    private Product findProduct(long id) {
        var rows = db.query(PRODUCT_SELECT + " WHERE p.id = ?", PRODUCT, id);
        if (rows.isEmpty()) throw missing("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm.");
        return rows.get(0);
    }

    private long activeAdmin(Authentication auth) {
        long actor = Long.parseLong(auth.getName());
        boolean admin = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_account WHERE id = ? AND status = 'ACTIVE' AND role = 'ADMIN')
                """, Boolean.class, actor));
        if (!admin) throw new Rejected(403, "ADMIN_REQUIRED", "Yêu cầu tài khoản quản trị đang hoạt động.");
        return actor;
    }

    private static void whitelist(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw invalid("Nội dung phải là object JSON.");
        var names = body.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!allowed.contains(field)) throw invalid("Không được gửi trường " + field + ".");
        }
    }

    private static String text(JsonNode body, String field, int min, int max, boolean required) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            if (required) throw invalid("Thiếu " + field + ".");
            return null;
        }
        if (!value.isTextual()) throw invalid(field + " phải là chuỗi.");
        String text = value.asText().strip();
        if (text.length() < min || text.length() > max) {
            throw invalid(field + " phải có từ " + min + " đến " + max + " ký tự.");
        }
        return text;
    }

    private static String choice(JsonNode body, String field, Set<String> values, boolean required) {
        String value = text(body, field, 1, 20, required);
        if (value != null && !values.contains(value)) throw invalid(field + " không hợp lệ.");
        return value;
    }

    private static String slug(JsonNode body) {
        String value = text(body, "slug", 1, 80, true);
        if (!value.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw invalid("slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.");
        }
        return value;
    }

    private static String sku(JsonNode body) {
        String value = text(body, "sku", 1, 64, true);
        if (!value.matches("[A-Za-z0-9._-]+")) throw invalid("sku chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang.");
        return value.toUpperCase(java.util.Locale.ROOT);
    }

    private static String imageUrl(JsonNode body) {
        String value = text(body, "imageUrl", 0, 500, false);
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("(https?://|/)[^\\s]+")) throw invalid("imageUrl phải là đường dẫn http(s) hoặc bắt đầu bằng /.");
        return value;
    }

    private static int integer(JsonNode body, String field, int min, int max) {
        JsonNode value = body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < min || value.intValue() > max) {
            throw invalid(field + " phải là số nguyên từ " + min + " đến " + max + ".");
        }
        return value.intValue();
    }

    private static long positiveLong(JsonNode body, String field) {
        JsonNode value = body.get(field);
        String text = value == null ? "" : value.isIntegralNumber() ? value.asText() : value.isTextual() ? value.asText() : "";
        if (!text.matches("[1-9][0-9]{0,17}")) throw invalid(field + " không hợp lệ.");
        return Long.parseLong(text);
    }

    private static BigDecimal price(JsonNode body) {
        JsonNode value = body.get("price");
        String text = value == null ? "" : value.isIntegralNumber() || value.isTextual() ? value.asText().strip() : "";
        if (!text.matches("[1-9][0-9]{0,11}")) throw invalid("price phải là số nguyên VND lớn hơn 0.");
        BigDecimal price = new BigDecimal(text);
        if (price.compareTo(MAX_PRICE) > 0) throw invalid("price vượt giới hạn.");
        return price;
    }

    private static long version(JsonNode body) {
        JsonNode value = body == null ? null : body.get("version");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw invalid("Thiếu version hợp lệ.");
        }
        return value.longValue();
    }

    private static long id(String value, String notFound) {
        if (!value.matches("[1-9][0-9]{0,17}")) throw missing(notFound, "Không tìm thấy dữ liệu.");
        return Long.parseLong(value);
    }

    /** An empty filter from a form (status=) means "no filter", not an invalid value. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void checkPage(int page, int size, String q) {
        if (page < 0 || size < 1 || size > 100 || q.length() > 200) throw invalidQuery();
    }

    private static long pages(long total, int size) {
        return total / size + (total % size == 0 ? 0 : 1);
    }

    private static Rejected invalid(String message) {
        return new Rejected(422, "INVALID_INPUT", message);
    }

    private static Rejected invalidQuery() {
        return new Rejected(400, "INVALID_QUERY", "Bộ lọc hoặc phân trang không hợp lệ.");
    }

    private static Rejected missing(String code, String message) {
        return new Rejected(404, code, message);
    }

    private static Rejected versionConflict() {
        return new Rejected(409, "VERSION_CONFLICT", "Dữ liệu đã thay đổi. Hãy tải lại.");
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<?> rejected(Rejected error) {
        return error(error.status, error.code, error.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicate() {
        return error(409, "DUPLICATE_VALUE", "SKU hoặc slug đã tồn tại.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed() {
        return error(400, "INVALID_REQUEST", "Yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể cập nhật danh mục.");
    }

    private static ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message,
                "fieldErrors", List.of(), "traceId", UUID.randomUUID().toString()));
    }

    record Page<T>(List<T> items, int page, int size, long totalElements, long totalPages) {}
    record Category(String id, String name, String slug, String status, long version, long productCount) {}
    record Product(String id, String sku, String name, String description, String species, String lifeStage,
            String brand, int weightGrams, String price, int availableStock, String imageUrl, String status,
            long contentVersion, long version, String categoryId, String categoryName, String categoryStatus) {}
    private record Current(long categoryId, String sku, String name, String description, String species,
            String lifeStage, String brand, int weightGrams, BigDecimal price, String imageUrl, String status,
            int stock, long version) {}
    private record Fields(String sku, String name, String description, String species, String lifeStage,
            String brand, int weightGrams, BigDecimal price, String imageUrl, String status) {}

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
