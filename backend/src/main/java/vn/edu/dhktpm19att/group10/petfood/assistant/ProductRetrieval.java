package vn.edu.dhktpm19att.group10.petfood.assistant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Reads candidate products for search and the assistant. Only products that customers can buy right now are
 * returned (ACTIVE product in an ACTIVE category), and species, life stage and budget are hard filters that are
 * never relaxed (BR22). A vector whose content_version is behind the product is ignored until re-indexed (AC25).
 */
@Repository
class ProductRetrieval {
    private static final String FIELDS = """
            SELECT p.id, p.sku, p.name, p.description, p.species, p.life_stage, p.brand, p.price, p.available_stock
            FROM product p JOIN category c ON c.id = p.category_id
            """;
    private static final RowMapper<Candidate> CANDIDATE = (rs, row) -> new Candidate(rs.getString("id"),
            rs.getString("sku"), rs.getString("name"), shorten(rs.getString("description")), rs.getString("species"),
            rs.getString("life_stage"), rs.getString("brand"), rs.getBigDecimal("price").toPlainString(),
            rs.getInt("available_stock"));

    private final JdbcTemplate db;

    ProductRetrieval(JdbcTemplate db) {
        this.db = db;
    }

    List<Candidate> semantic(float[] query, String modelId, Filters filters, int limit) {
        var args = new ArrayList<Object>();
        args.add(modelId);
        String where = where(filters, args);
        args.add(vector(query));
        args.add(limit);
        return db.query(FIELDS + """
                JOIN product_embedding e ON e.product_id = p.id AND e.model_id = ?
                    AND e.content_version = p.content_version
                """ + where + " ORDER BY e.embedding <=> CAST(? AS vector), p.id LIMIT ?", CANDIDATE, args.toArray());
    }

    /** Fallback when the model is unavailable: products containing the most query words. */
    List<Candidate> keyword(String query, Filters filters, int limit) {
        var words = Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 2).distinct().limit(8).toList();
        var args = new ArrayList<Object>();
        String where = where(filters, args);
        String haystack = "lower(p.name || ' ' || p.brand || ' ' || p.description || ' ' || p.sku)";
        var score = new StringBuilder("0");
        var scoreArgs = new ArrayList<Object>();
        for (String word : words) {
            score.append(" + CASE WHEN strpos(").append(haystack).append(", ?) > 0 THEN 1 ELSE 0 END");
            scoreArgs.add(word);
        }
        var all = new ArrayList<Object>(scoreArgs);
        all.addAll(args);
        if (!words.isEmpty()) {
            all.addAll(scoreArgs);
        }
        all.add(limit);
        String sql = "SELECT * FROM (SELECT p.id, p.sku, p.name, p.description, p.species, p.life_stage, p.brand,"
                + " p.price, p.available_stock, (" + score + ") AS score"
                + " FROM product p JOIN category c ON c.id = p.category_id" + where
                + (words.isEmpty() ? "" : " AND (" + score + ") > 0")
                + ") ranked ORDER BY score DESC, (available_stock > 0) DESC, name, id LIMIT ?";
        return db.query(sql, CANDIDATE, all.toArray());
    }

    private static String where(Filters filters, List<Object> args) {
        var sql = new StringBuilder(" WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE'");
        if (filters.species() != null) {
            sql.append(" AND p.species IN (?, 'BOTH')");
            args.add(filters.species());
        }
        if (filters.lifeStage() != null) {
            sql.append(" AND p.life_stage IN (?, 'ALL')");
            args.add(filters.lifeStage());
        }
        if (filters.maxPrice() != null) {
            sql.append(" AND p.price <= ?");
            args.add(filters.maxPrice());
        }
        return sql.toString();
    }

    static String vector(float[] values) {
        var text = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) text.append(',');
            text.append(values[i]);
        }
        return text.append(']').toString();
    }

    private static String shorten(String description) {
        return description.length() <= 400 ? description : description.substring(0, 400) + "…";
    }

    record Filters(String species, String lifeStage, BigDecimal maxPrice) {}

    record Candidate(String id, String sku, String name, String description, String species, String lifeStage,
            String brand, String price, int availableStock) {}
}
