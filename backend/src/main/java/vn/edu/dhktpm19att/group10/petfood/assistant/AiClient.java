package vn.edu.dhktpm19att.group10.petfood.assistant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The only way the backend talks to a model. It gets text in and text or vectors out; it has no access to the
 * database, SQL or orders, so model output can never write anything by itself (BR22, AC26).
 */
public interface AiClient {
    /** Vector length stored in product_embedding.embedding. Changing it needs a new migration. */
    int DIMENSIONS = 768;

    boolean enabled();

    /** Identifies the embedding model; vectors from different models are never compared. */
    String embeddingModel();

    /** Returns a unit-length vector of {@link #DIMENSIONS} values. */
    float[] embed(String text, Purpose purpose);

    /** Asks the chat model for JSON that matches {@code schema} and returns it parsed. */
    JsonNode answerJson(String systemInstruction, String userMessage, JsonNode schema);

    enum Purpose { DOCUMENT, QUERY }

    /** Disabled, timed out, over quota or returned something unusable: callers fall back to plain search. */
    class Unavailable extends RuntimeException {
        public Unavailable(String message) {
            super(message);
        }

        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
