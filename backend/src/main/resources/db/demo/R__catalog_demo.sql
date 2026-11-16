-- Local demo data, loaded only when PETFOOD_FLYWAY_LOCATIONS includes classpath:db/demo.
-- Re-running keeps existing rows (ON CONFLICT DO NOTHING). Brands and products are fictional examples.

-- ---- Categories ----
INSERT INTO category (name, slug, status) VALUES
    ('Thức ăn cho chó', 'demo-dog-food', 'ACTIVE'),
    ('Thức ăn cho mèo', 'demo-cat-food', 'ACTIVE'),
    ('Pate & thức ăn ướt cho chó', 'demo-dog-wet', 'ACTIVE'),
    ('Pate & súp cho mèo', 'demo-cat-wet', 'ACTIVE'),
    ('Bánh thưởng & đồ ăn vặt', 'demo-treats', 'ACTIVE'),
    ('Danh mục ẩn (demo)', 'demo-hidden', 'INACTIVE')
ON CONFLICT (slug) DO NOTHING;

-- ---- Products ----
-- Species DOG/CAT/BOTH, life stage JUNIOR/ADULT/SENIOR/ALL. A few rows are sold out, hidden or have one unit
-- left on purpose, to demo those cases.
INSERT INTO product (category_id, sku, name, description, species, life_stage, brand,
                     weight_grams, price, available_stock, status)
SELECT c.id, d.sku, d.name, d.description || ' Sản phẩm minh họa.', d.species, d.life_stage, d.brand,
       d.weight_grams, d.price, d.stock, d.status
FROM (VALUES
    ('demo-dog-food', 'DEMO-DOG-01', 'Hạt gà cho chó trưởng thành', 'Thức ăn khô vị gà, hạt vừa cho chó từ 1 tuổi.', 'DOG', 'ADULT', 'PetFood Demo', 1500, 185000, 12, 'ACTIVE'),
    ('demo-dog-food', 'DEMO-DOG-02', 'Hạt cá hồi cho chó con', 'Hạt nhỏ mềm vị cá hồi cho chó con từ 2 đến 12 tháng tuổi.', 'DOG', 'JUNIOR', 'Cún Khỏe', 2000, 245000, 15, 'ACTIVE'),
    ('demo-dog-food', 'DEMO-DOG-03', 'Hạt gà và gạo cho chó trưởng thành 3 kg', 'Túi lớn tiết kiệm, vị gà và gạo, phù hợp chó giống vừa và lớn.', 'DOG', 'ADULT', 'Hạt Vàng', 3000, 329000, 20, 'ACTIVE'),
    ('demo-dog-food', 'DEMO-DOG-04', 'Hạt thịt cừu cho chó lớn tuổi', 'Hạt vị cừu, kích thước nhỏ dễ nhai cho chó từ 7 tuổi.', 'DOG', 'SENIOR', 'Cún Khỏe', 1500, 215000, 8, 'ACTIVE'),
    ('demo-dog-food', 'DEMO-DOG-05', 'Hạt vị bò cho chó giống nhỏ', 'Hạt nhỏ vị bò cho chó dưới 10 kg như Poodle, Chihuahua.', 'DOG', 'ADULT', 'Hạt Vàng', 1000, 139000, 25, 'ACTIVE'),
    ('demo-dog-food', 'DEMO-DOG-06', 'Hạt không ngũ cốc vị vịt cho chó', 'Công thức không ngũ cốc, đạm chính từ thịt vịt.', 'DOG', 'ADULT', 'Biển Xanh', 2000, 389000, 6, 'ACTIVE'),
    ('demo-dog-food', 'DEMO-DOG-07', 'Hạt gà rau củ cho chó mọi lứa tuổi 5 kg', 'Vị gà kèm cà rốt và bí đỏ, dùng được cho chó mọi độ tuổi.', 'DOG', 'ALL', 'PetFood Demo', 5000, 459000, 10, 'ACTIVE'),
    ('demo-dog-food', 'DEMO-DOG-08', 'Hạt cá trắng và khoai lang cho chó', 'Ít thành phần, đạm từ cá trắng. Chỉ còn một túi để thử mua đồng thời.', 'DOG', 'ADULT', 'Biển Xanh', 1500, 265000, 1, 'ACTIVE'),
    ('demo-dog-wet', 'DEMO-DOGW-01', 'Pate gà cho chó lon 400 g', 'Pate mềm vị gà, trộn cùng hạt hoặc ăn riêng.', 'DOG', 'ALL', 'Cún Khỏe', 400, 45000, 40, 'ACTIVE'),
    ('demo-dog-wet', 'DEMO-DOGW-02', 'Pate bò rau củ cho chó con', 'Gói nhỏ vị bò rau củ, kết cấu mịn cho chó con.', 'DOG', 'JUNIOR', 'Hạt Vàng', 100, 22000, 60, 'ACTIVE'),
    ('demo-dog-wet', 'DEMO-DOGW-03', 'Thức ăn ướt vị cừu cho chó lớn tuổi', 'Miếng thịt cừu mềm trong nước sốt. Đang hết hàng.', 'DOG', 'SENIOR', 'Cún Khỏe', 400, 52000, 0, 'ACTIVE'),
    ('demo-dog-wet', 'DEMO-DOGW-04', 'Gói sốt thịt gà cho chó', 'Gói 85 g thịt gà sốt, rưới lên hạt để tăng vị.', 'DOG', 'ADULT', 'PetFood Demo', 85, 18000, 80, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-01', 'Pâté cá cho mèo trưởng thành', 'Thức ăn ướt vị cá. Đang hết hàng.', 'CAT', 'ADULT', 'PetFood Demo', 85, 28000, 0, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-02', 'Hạt cho mèo con', 'Sản phẩm ẩn dùng kiểm thử.', 'CAT', 'JUNIOR', 'PetFood Demo', 400, 79000, 8, 'INACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-03', 'Hạt cá ngừ cho mèo trưởng thành 1,5 kg', 'Hạt giòn vị cá ngừ cho mèo từ 1 tuổi.', 'CAT', 'ADULT', 'MeoMeo Kitchen', 1500, 199000, 18, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-04', 'Hạt cá hồi cho mèo con', 'Hạt nhỏ vị cá hồi cho mèo con từ 2 đến 12 tháng tuổi.', 'CAT', 'JUNIOR', 'MeoMeo Kitchen', 1000, 165000, 12, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-05', 'Hạt vị gà cho mèo lớn tuổi', 'Hạt mềm hơn, vị gà, cho mèo từ 7 tuổi.', 'CAT', 'SENIOR', 'Biển Xanh', 1200, 210000, 7, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-06', 'Hạt gà tây cho mèo nuôi trong nhà', 'Vị gà tây cho mèo ít vận động trong nhà.', 'CAT', 'ADULT', 'Hạt Vàng', 2000, 279000, 9, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-07', 'Hạt hải sản tổng hợp cho mèo 400 g', 'Gói nhỏ vị cá và tôm, hợp để thử vị.', 'CAT', 'ADULT', 'MeoMeo Kitchen', 400, 69000, 30, 'ACTIVE'),
    ('demo-cat-food', 'DEMO-CAT-08', 'Hạt không ngũ cốc vị vịt cho mèo', 'Công thức không ngũ cốc. Chỉ còn một túi để thử mua đồng thời.', 'CAT', 'ADULT', 'Biển Xanh', 1500, 319000, 1, 'ACTIVE'),
    ('demo-cat-wet', 'DEMO-CATW-01', 'Pate cá ngừ cho mèo lon 160 g', 'Pate cá ngừ mịn, mèo mọi lứa tuổi đều dùng được.', 'CAT', 'ALL', 'MeoMeo Kitchen', 160, 26000, 70, 'ACTIVE'),
    ('demo-cat-wet', 'DEMO-CATW-02', 'Pate gà mềm cho mèo con', 'Gói 85 g vị gà, kết cấu mịn cho mèo con.', 'CAT', 'JUNIOR', 'PetFood Demo', 85, 19000, 90, 'ACTIVE'),
    ('demo-cat-wet', 'DEMO-CATW-03', 'Súp thưởng cá hồi cho mèo (4 thanh)', 'Súp dạng thanh vị cá hồi để thưởng hoặc trộn với hạt.', 'CAT', 'ALL', 'MeoMeo Kitchen', 60, 35000, 120, 'ACTIVE'),
    ('demo-cat-wet', 'DEMO-CATW-04', 'Pate cá thu cho mèo lớn tuổi', 'Pate cá thu mềm cho mèo từ 7 tuổi.', 'CAT', 'SENIOR', 'Biển Xanh', 85, 21000, 45, 'ACTIVE'),
    ('demo-cat-wet', 'DEMO-CATW-05', 'Pate gà và gan cho mèo trưởng thành', 'Lon 400 g vị gà và gan. Đang hết hàng.', 'CAT', 'ADULT', 'Hạt Vàng', 400, 49000, 0, 'ACTIVE'),
    ('demo-treats', 'DEMO-TRT-01', 'Bánh quy xương sữa cho chó', 'Bánh quy hình xương vị sữa để thưởng khi huấn luyện.', 'DOG', 'ALL', 'Cún Khỏe', 200, 39000, 50, 'ACTIVE'),
    ('demo-treats', 'DEMO-TRT-02', 'Que gặm gà sấy cho chó', 'Que thịt gà sấy khô cho chó trưởng thành.', 'DOG', 'ADULT', 'Hạt Vàng', 100, 55000, 35, 'ACTIVE'),
    ('demo-treats', 'DEMO-TRT-03', 'Bánh thưởng cá bào cho mèo', 'Cá bào sấy mỏng, rắc lên hạt hoặc thưởng trực tiếp.', 'CAT', 'ALL', 'MeoMeo Kitchen', 50, 32000, 60, 'ACTIVE'),
    ('demo-treats', 'DEMO-TRT-04', 'Cỏ mèo sấy khô', 'Cỏ mèo sấy khô dạng lá vụn.', 'CAT', 'ALL', 'PetFood Demo', 30, 25000, 40, 'ACTIVE'),
    ('demo-treats', 'DEMO-TRT-05', 'Bánh thưởng huấn luyện vị bò cho chó và mèo', 'Viên nhỏ vị bò, dùng chung cho chó và mèo.', 'BOTH', 'ALL', 'PetFood Demo', 150, 45000, 55, 'ACTIVE'),
    ('demo-treats', 'DEMO-TRT-06', 'Sữa dê bột cho chó mèo con', 'Bột sữa dê pha với nước ấm cho chó mèo con.', 'BOTH', 'JUNIOR', 'Biển Xanh', 200, 89000, 20, 'ACTIVE'),
    ('demo-treats', 'DEMO-TRT-07', 'Bánh thưởng ngừng bán', 'Sản phẩm ẩn dùng kiểm thử.', 'DOG', 'ALL', 'PetFood Demo', 100, 30000, 10, 'INACTIVE'),
    ('demo-hidden', 'DEMO-HIDDEN-01', 'Hạt cho chó con', 'Sản phẩm thuộc danh mục ẩn dùng kiểm thử.', 'DOG', 'JUNIOR', 'PetFood Demo', 500, 85000, 4, 'ACTIVE')
) AS d(slug, sku, name, description, species, life_stage, brand, weight_grams, price, stock, status)
JOIN category c ON c.slug = d.slug
ON CONFLICT (sku) DO NOTHING;

-- ---- Demo customers ----
-- Hashes of random passwords that were thrown away: nobody can sign in with these accounts. They only own the
-- demo orders. Create your own ADMIN with PETFOOD_ADMIN_EMAIL/PETFOOD_ADMIN_PASSWORD.
INSERT INTO user_account (email_normalized, display_name, password_hash, phone, role, status) VALUES
    ('demo-khach-1@petfood.local', 'Khách demo 1', '65826a56e5884a32ba3f4c8b559fed2a6995228d0f7a7e63c785ea2c84dd5384a1f13aeeecf004a6180cf563a6f00bdb', '0900000001', 'CUSTOMER', 'ACTIVE'),
    ('demo-khach-2@petfood.local', 'Khách demo 2', '0ab8737df236ae25491e411d5e0e7229211a45c84fa6a80c4f36c22a5a8306d9a1cbb5b0ff22b90cd967cc09c0a50275', '0900000002', 'CUSTOMER', 'ACTIVE'),
    ('demo-khach-3@petfood.local', 'Khách demo 3', 'a204ffe1e072cfb63737dfd6c28896bb3216567fdbb027ddea51b887c3216d72f36aa656cf7f65b7a17306b95194e5f1', '0900000003', 'CUSTOMER', 'ACTIVE')
ON CONFLICT (email_normalized) DO NOTHING;

-- ---- Demo orders in every status ----
-- Totals match the lines. Demo orders do not change stock and send no email.
INSERT INTO customer_order (id, order_code, customer_id, status, payment_status, recipient_name, phone,
                            address_text, subtotal, shipping_fee, grand_total, created_at, paid_at)
SELECT CAST(o.id AS uuid), o.code, u.id, o.status, o.payment, u.display_name, u.phone,
       'Địa chỉ demo, không giao thật', o.total, 0, o.total,
       now() - (o.days * interval '1 day'), CASE WHEN o.payment = 'PAID' THEN now() - (o.paid_days * interval '1 day') END
FROM (VALUES
    ('00000000-0000-4000-8000-000000000001', 'PF-DEMO-0001', 'demo-khach-1@petfood.local', 'COMPLETED', 'PAID', 509000, 6, 3),
    ('00000000-0000-4000-8000-000000000002', 'PF-DEMO-0002', 'demo-khach-2@petfood.local', 'SHIPPING', 'UNPAID', 355000, 2, 0),
    ('00000000-0000-4000-8000-000000000003', 'PF-DEMO-0003', 'demo-khach-3@petfood.local', 'CONFIRMED', 'UNPAID', 90000, 1, 0),
    ('00000000-0000-4000-8000-000000000004', 'PF-DEMO-0004', 'demo-khach-1@petfood.local', 'PENDING', 'UNPAID', 133000, 0, 0),
    ('00000000-0000-4000-8000-000000000005', 'PF-DEMO-0005', 'demo-khach-2@petfood.local', 'CANCELLED', 'UNPAID', 139000, 4, 0),
    ('00000000-0000-4000-8000-000000000006', 'PF-DEMO-0006', 'demo-khach-3@petfood.local', 'COMPLETED', 'PAID', 105000, 12, 10)
) AS o(id, code, email, status, payment, total, days, paid_days)
JOIN user_account u ON u.email_normalized = o.email
ON CONFLICT (id) DO NOTHING;

INSERT INTO order_item (order_id, product_id, sku_snapshot, name_snapshot, unit_price, quantity, line_total)
SELECT CAST(i.order_id AS uuid), p.id, p.sku, p.name, p.price, i.quantity, p.price * i.quantity
FROM (VALUES
    ('00000000-0000-4000-8000-000000000001', 'DEMO-DOG-03', 1),
    ('00000000-0000-4000-8000-000000000001', 'DEMO-DOGW-01', 4),
    ('00000000-0000-4000-8000-000000000002', 'DEMO-CAT-03', 1),
    ('00000000-0000-4000-8000-000000000002', 'DEMO-CATW-01', 6),
    ('00000000-0000-4000-8000-000000000003', 'DEMO-TRT-05', 2),
    ('00000000-0000-4000-8000-000000000004', 'DEMO-CAT-07', 1),
    ('00000000-0000-4000-8000-000000000004', 'DEMO-TRT-03', 2),
    ('00000000-0000-4000-8000-000000000005', 'DEMO-DOG-05', 1),
    ('00000000-0000-4000-8000-000000000006', 'DEMO-CATW-03', 3)
) AS i(order_id, sku, quantity)
JOIN product p ON p.sku = i.sku
JOIN customer_order o ON o.id = CAST(i.order_id AS uuid)
ON CONFLICT (order_id, product_id) DO NOTHING;

INSERT INTO order_history (order_id, actor_id, action, to_status)
SELECT o.id, NULL, 'ORDER_PLACED', 'PENDING' FROM customer_order o
WHERE o.order_code LIKE 'PF-DEMO-%'
  AND NOT EXISTS (SELECT 1 FROM order_history h WHERE h.order_id = o.id AND h.action = 'ORDER_PLACED');

-- ---- Demo tracking (is_demo = true) ----
-- The admin dashboard hides demo rows unless includeDemo=true. 40 sessions view a product, 18 add to cart,
-- 10 start checkout and 6 place an order; 60 recommendation impressions get 9 clicks.
WITH visible AS (
    SELECT p.id, row_number() OVER (ORDER BY p.sku) - 1 AS n, count(*) OVER () AS total
    FROM product p JOIN category c ON c.id = p.category_id
    WHERE p.sku LIKE 'DEMO-%' AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'
), steps AS (
    SELECT s, step, name FROM generate_series(1, 40) AS s
    CROSS JOIN (VALUES (1, 'product_view'), (2, 'add_to_cart'), (3, 'checkout_start'), (4, 'order_placed'))
        AS f(step, name)
    WHERE (step = 1) OR (step = 2 AND s <= 18) OR (step = 3 AND s <= 10) OR (step = 4 AND s <= 6)
)
INSERT INTO behavior_event (event_id, name, subject_key, anon_token, product_id, server_received_at, is_demo)
SELECT CAST(md5('demo-event-' || s || '-' || step) AS uuid), name, 'anon:demo-session-' || s,
       'demo-session-' || s,
       CASE WHEN name = 'order_placed' THEN NULL ELSE (SELECT v.id FROM visible v WHERE v.n = s % v.total) END,
       now() - (s * interval '3 hours') + (step * interval '2 minutes'), true
FROM steps
ON CONFLICT (event_id) DO NOTHING;

WITH visible AS (
    SELECT p.id, row_number() OVER (ORDER BY p.sku) - 1 AS n, count(*) OVER () AS total
    FROM product p JOIN category c ON c.id = p.category_id
    WHERE p.sku LIKE 'DEMO-%' AND p.status = 'ACTIVE' AND c.status = 'ACTIVE' AND p.available_stock > 0
)
INSERT INTO recommendation_impression (impression_id, recommendation_id, subject_key, anon_token, product_id,
                                       position, personalized, is_demo, created_at)
SELECT CAST(md5('demo-impression-' || s || '-' || pos) AS uuid), CAST(md5('demo-recommendation-' || s) AS uuid),
       'anon:demo-session-' || s, 'demo-session-' || s,
       (SELECT v.id FROM visible v WHERE v.n = (s * 2 + pos) % v.total), pos, s % 2 = 0, true,
       now() - (s * interval '3 hours')
FROM generate_series(1, 30) AS s CROSS JOIN generate_series(0, 1) AS pos
ON CONFLICT (impression_id) DO NOTHING;

INSERT INTO behavior_event (event_id, name, subject_key, anon_token, product_id, impression_id,
                            server_received_at, is_demo)
SELECT CAST(md5('demo-click-' || i.impression_id) AS uuid), 'recommendation_click', i.subject_key, i.anon_token,
       i.product_id, i.impression_id, i.created_at + interval '1 minute', true
FROM recommendation_impression i
WHERE i.is_demo AND i.position = 0 AND i.anon_token IN (
    SELECT 'demo-session-' || s FROM generate_series(1, 30) AS s WHERE s % 3 = 0 AND s <= 27)
ON CONFLICT (event_id) DO NOTHING;
