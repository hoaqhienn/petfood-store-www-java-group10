package vn.edu.dhktpm19att.group10.petfood.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1")
class CatalogController {
    private final CatalogQueries catalog;

    CatalogController(CatalogQueries catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    List<CatalogQueries.Category> categories() {
        return catalog.categories();
    }

    @GetMapping("/products")
    CatalogQueries.ProductPage products(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "") String species,
            @RequestParam(defaultValue = "") String lifeStage,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        if (page < 0 || size < 1 || size > 100 || q.length() > 200
                || species.length() > 32 || lifeStage.length() > 32
                || (categoryId != null && categoryId < 1)
                || invalidPrice(minPrice) || invalidPrice(maxPrice)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw new IllegalArgumentException("Bộ lọc hoặc thông tin phân trang không hợp lệ.");
        }
        return catalog.products(q.strip(), categoryId, species, lifeStage, minPrice, maxPrice, page, size, sort);
    }

    @GetMapping("/products/{id}")
    ResponseEntity<?> product(@PathVariable String id) {
        if (!id.matches("[1-9][0-9]{0,18}")) {
            return error(404, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm.");
        }
        try {
            var product = catalog.product(Long.parseLong(id));
            if (product.isPresent()) return ResponseEntity.ok(product.get());
        } catch (NumberFormatException ignored) {
            // An ID outside the database's bigint range cannot exist.
        }
        return error(404, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm.");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalidQuery() {
        return error(400, "INVALID_QUERY", "Tham số truy vấn không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return error(503, "DEPENDENCY_UNAVAILABLE", "Danh mục tạm thời chưa khả dụng.");
    }

    private static boolean invalidPrice(BigDecimal price) {
        return price != null && (price.signum() < 0 || price.scale() > 0
                || price.precision() - (long) price.scale() > 18);
    }

    private static ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code, "message", message, "fieldErrors", List.of(),
                "traceId", UUID.randomUUID().toString()));
    }
}
