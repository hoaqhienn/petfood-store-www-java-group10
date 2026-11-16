-- Audit fields for admin status changes and quantity edits (BR16-BR17).
ALTER TABLE order_history ADD COLUMN from_status text;
ALTER TABLE order_history ADD COLUMN sku_snapshot text;
ALTER TABLE order_history ADD COLUMN quantity_change integer;
