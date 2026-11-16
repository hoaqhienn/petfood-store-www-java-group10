-- Product vectors for semantic search and the assistant (BR23). One row per product and embedding model,
-- so vectors of different models or dimensions are never compared with each other.
CREATE TABLE product_embedding (
    product_id bigint NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    model_id text NOT NULL,
    content_version bigint NOT NULL,
    embedding vector(768) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, model_id)
);
CREATE INDEX product_embedding_hnsw_idx ON product_embedding USING hnsw (embedding vector_cosine_ops);
