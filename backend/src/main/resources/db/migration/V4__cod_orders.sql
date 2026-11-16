CREATE TABLE customer_order (
    id uuid PRIMARY KEY,
    order_code text NOT NULL UNIQUE,
    customer_id bigint NOT NULL REFERENCES user_account(id) ON DELETE RESTRICT,
    status text NOT NULL DEFAULT 'PENDING',
    payment_method text NOT NULL DEFAULT 'COD',
    payment_status text NOT NULL DEFAULT 'UNPAID',
    recipient_name text NOT NULL,
    phone text NOT NULL,
    address_text text NOT NULL,
    subtotal numeric NOT NULL,
    shipping_fee numeric NOT NULL,
    grand_total numeric NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    paid_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE order_item (
    order_id uuid NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    product_id bigint NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    sku_snapshot text NOT NULL,
    name_snapshot text NOT NULL,
    unit_price numeric NOT NULL,
    quantity integer NOT NULL,
    line_total numeric NOT NULL,
    PRIMARY KEY (order_id, product_id)
);
CREATE TABLE checkout_request (
    customer_id bigint NOT NULL REFERENCES user_account(id) ON DELETE RESTRICT,
    idempotency_key text NOT NULL,
    fingerprint text NOT NULL,
    cart_token text NOT NULL,
    cart_revision bigint NOT NULL,
    order_id uuid NOT NULL UNIQUE REFERENCES customer_order(id) ON DELETE RESTRICT,
    PRIMARY KEY (customer_id, idempotency_key),
    UNIQUE (cart_token, cart_revision)
);
CREATE TABLE order_history (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    actor_id bigint REFERENCES user_account(id) ON DELETE SET NULL,
    action text NOT NULL,
    to_status text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE inventory_movement (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint REFERENCES product(id) ON DELETE SET NULL,
    sku_snapshot text NOT NULL,
    order_id uuid REFERENCES customer_order(id) ON DELETE RESTRICT,
    actor_id bigint REFERENCES user_account(id) ON DELETE SET NULL,
    delta integer NOT NULL,
    reason text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
