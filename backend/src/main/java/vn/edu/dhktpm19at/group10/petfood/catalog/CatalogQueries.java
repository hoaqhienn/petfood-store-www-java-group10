package vn.edu.dhktpm19at.group10.petfood.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class CatalogQueries {
    private static final String VISIBLE = """
            FROM product p JOIN category c ON c.id = p.category_id
            WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE'
            """;
    private static final String FIELDS = """
            SELECT p.id, p.sku, p.name, p.description, p.species, p.life_stage,
                   p.brand, p.weight_grams, p.price, p.available_stock, p.image_url,
                   c.id AS category_id, c.name AS category_name
            """;
    private static final RowMapper<Product> PRODUCT = (rs, row) -> new Product(
            rs.getString("id"), rs.getString("sku"), rs.getString("name"),
            rs.getString("description"), rs.getString("species"), rs.getString("life_stage"),
            rs.getString("brand"), rs.getInt("weight_grams"), rs.getBigDecimal("price").toPlainString(),
            rs.getInt("available_stock"), rs.getString("image_url"),
            rs.getString("category_id"), rs.getString("category_name"));

    private final NamedParameterJdbcTemplate jdbc;

    CatalogQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Category> categories() {
        return jdbc.query("""
                SELECT id, name, slug FROM category WHERE status = 'ACTIVE' ORDER BY name, id
                """, (rs, row) -> new Category(rs.getString("id"), rs.getString("name"), rs.getString("slug")));
    }

    Optional<Product> product(long id) {
        return jdbc.query(FIELDS + VISIBLE + " AND p.id = :id",
                new MapSqlParameterSource("id", id), PRODUCT).stream().findFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductPage products(String q, Long categoryId, String species, String lifeStage,
            BigDecimal minPrice, BigDecimal maxPrice, int page, int size, String sort) {
        String order = switch (sort) {
            case "name,asc" -> "p.name ASC";
            case "name,desc" -> "p.name DESC";
            case "price,asc" -> "p.price ASC";
            case "price,desc" -> "p.price DESC";
            default -> throw new IllegalArgumentException("Sort không hợp lệ.");
        };
        var where = new StringBuilder(VISIBLE);
        var params = new MapSqlParameterSource();
        if (!q.isEmpty()) {
            where.append(" AND (strpos(lower(p.name), lower(:q)) > 0 OR strpos(lower(p.sku), lower(:q)) > 0)");
            params.addValue("q", q);
        }
        filter(where, params, " AND c.id = :categoryId", "categoryId", categoryId);
        filter(where, params, " AND p.species = :species", "species", species.isBlank() ? null : species);
        filter(where, params, " AND p.life_stage = :lifeStage", "lifeStage", lifeStage.isBlank() ? null : lifeStage);
        filter(where, params, " AND p.price >= :minPrice", "minPrice", minPrice);
        filter(where, params, " AND p.price <= :maxPrice", "maxPrice", maxPrice);
        long total = jdbc.queryForObject("SELECT count(*) " + where, params, Long.class);
        params.addValue("size", size).addValue("offset", (long) page * size);
        var products = jdbc.query(FIELDS + where + " ORDER BY " + order + ", p.id LIMIT :size OFFSET :offset",
                params, PRODUCT);
        return new ProductPage(products, page, size, total, total / size + (total % size == 0 ? 0 : 1));
    }

    private static void filter(StringBuilder where, MapSqlParameterSource params,
            String clause, String name, Object value) {
        if (value != null) {
            where.append(clause);
            params.addValue(name, value);
        }
    }

    record Category(String id, String name, String slug) {}
    record Product(String id, String sku, String name, String description, String species, String lifeStage,
            String brand, int weightGrams, String price, int availableStock, String imageUrl,
            String categoryId, String categoryName) {}
    record ProductPage(List<Product> items, int page, int size, long totalElements, long totalPages) {}
}
