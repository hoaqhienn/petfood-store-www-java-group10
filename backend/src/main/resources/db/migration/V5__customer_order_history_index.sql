CREATE INDEX customer_order_history_idx ON customer_order (customer_id, created_at DESC, id DESC);
