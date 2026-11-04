-- Local demo data. Re-running this script preserves existing products.
INSERT INTO category (name, slug, status) VALUES
    ('Thức ăn cho chó', 'demo-dog-food', 'ACTIVE'),
    ('Thức ăn cho mèo', 'demo-cat-food', 'ACTIVE'),
    ('Danh mục ẩn (demo)', 'demo-hidden', 'INACTIVE')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product (category_id, sku, name, description, species, life_stage, brand,
                     weight_grams, price, available_stock, status)
SELECT c.id, d.sku, d.name, d.description, d.species, d.life_stage, 'PetFood Demo',
       d.weight_grams, d.price, d.stock, d.status
FROM (VALUES
    ('demo-dog-food', 'DEMO-DOG-01', 'Hạt gà cho chó trưởng thành', 'Thức ăn khô vị gà. Sản phẩm minh họa.', 'DOG', 'ADULT', 1500, 185000, 12, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-01', 'Pâté cá cho mèo trưởng thành', 'Thức ăn ướt vị cá. Sản phẩm minh họa.', 'CAT', 'ADULT', 85, 28000, 0, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-02', 'Hạt cho mèo con', 'Sản phẩm ẩn dùng kiểm thử.', 'CAT', 'JUNIOR', 400, 79000, 8, 'INACTIVE'),
    ('demo-hidden', 'DEMO-HIDDEN-01', 'Hạt cho chó con', 'Sản phẩm thuộc danh mục ẩn dùng kiểm thử.', 'DOG', 'JUNIOR', 500, 85000, 4, 'ACTIVE')
) AS d(slug, sku, name, description, species, life_stage, weight_grams, price, stock, status)
JOIN category c ON c.slug = d.slug
ON CONFLICT (sku) DO NOTHING;
