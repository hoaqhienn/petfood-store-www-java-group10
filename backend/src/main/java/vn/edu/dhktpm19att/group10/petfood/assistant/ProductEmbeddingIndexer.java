package vn.edu.dhktpm19att.group10.petfood.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps product vectors in step with the catalog. Admin edits bump content_version, which makes the old vector
 * stale; this job re-embeds ACTIVE products in small batches so the API quota is spent gradually.
 */
@Component
class ProductEmbeddingIndexer {
    private static final Logger log = LoggerFactory.getLogger(ProductEmbeddingIndexer.class);
    private static final int BATCH = 20;

    private final JdbcTemplate db;
    private final AiClient ai;

    ProductEmbeddingIndexer(JdbcTemplate db, AiClient ai) {
        this.db = db;
        this.ai = ai;
    }

    @Scheduled(fixedDelayString = "${app.ai.index-interval-ms:60000}", initialDelay = 10000)
    void scheduled() {
        if (!ai.enabled()) return;
        try {
            indexBatch();
        } catch (AiClient.Unavailable unavailable) {
            log.warn("Product indexing paused: {}", unavailable.getMessage());
        }
    }

    /** Embeds up to one batch of missing or stale products and returns how many were stored. */
    int indexBatch() {
        String model = ai.embeddingModel();
        var due = db.query("""
                SELECT p.id, p.content_version, p.name, p.brand, p.species, p.life_stage, p.weight_grams,
                       p.description, c.name AS category
                FROM product p JOIN category c ON c.id = p.category_id
                LEFT JOIN product_embedding e ON e.product_id = p.id AND e.model_id = ?
                WHERE p.status = 'ACTIVE' AND (e.product_id IS NULL OR e.content_version <> p.content_version)
                ORDER BY p.id LIMIT ?
                """, (rs, row) -> new Due(rs.getLong("id"), rs.getLong("content_version"), document(
                rs.getString("name"), rs.getString("brand"), rs.getString("category"), rs.getString("species"),
                rs.getString("life_stage"), rs.getInt("weight_grams"), rs.getString("description"))), model, BATCH);
        int stored = 0;
        for (var product : due) {
            float[] vector = ai.embed(product.text(), AiClient.Purpose.DOCUMENT);
            if (vector.length != AiClient.DIMENSIONS) {
                throw new AiClient.Unavailable("Embedding có số chiều không đúng " + AiClient.DIMENSIONS + ".");
            }
            // The version check stops an older read from overwriting a vector of a newer edit.
            stored += db.update("""
                    INSERT INTO product_embedding (product_id, model_id, content_version, embedding)
                    SELECT ?, ?, ?, CAST(? AS vector) WHERE EXISTS (
                        SELECT 1 FROM product WHERE id = ? AND content_version = ?)
                    ON CONFLICT (product_id, model_id) DO UPDATE
                    SET content_version = EXCLUDED.content_version, embedding = EXCLUDED.embedding, updated_at = now()
                    WHERE product_embedding.content_version <= EXCLUDED.content_version
                    """, product.id(), model, product.version(), ProductRetrieval.vector(vector), product.id(),
                    product.version());
        }
        return stored;
    }

    /** Only reviewed catalog fields are embedded; no customer data goes to the model (BR25). */
    static String document(String name, String brand, String category, String species, String lifeStage,
            int weightGrams, String description) {
        return "Sản phẩm: " + name + "\nThương hiệu: " + brand + "\nDanh mục: " + category
                + "\nLoài: " + species + "\nĐộ tuổi: " + lifeStage + "\nKhối lượng: " + weightGrams + " g"
                + "\nMô tả: " + description;
    }

    private record Due(long id, long version, String text) {}
}
