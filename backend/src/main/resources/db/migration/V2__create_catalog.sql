CREATE TABLE category (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name text NOT NULL,
    slug text NOT NULL UNIQUE,
    status text NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE product (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id bigint NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    sku text NOT NULL UNIQUE,
    name text NOT NULL,
    description text NOT NULL,
    species text NOT NULL,
    life_stage text NOT NULL,
    brand text NOT NULL,
    weight_grams integer NOT NULL,
    price numeric(18,0) NOT NULL,
    available_stock integer NOT NULL,
    image_url text,
    status text NOT NULL,
    content_version bigint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0
);

CREATE INDEX product_category_status_idx ON product (category_id, status);
