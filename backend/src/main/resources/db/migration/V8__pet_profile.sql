-- Customer pet profiles (BR21). Deleting an account removes its pets; orders never reference pets.
CREATE TABLE pet_profile (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id bigint NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    name text NOT NULL,
    species text NOT NULL,
    birth_date date,
    estimated_age_months integer,
    weight_grams integer,
    preferences text,
    created_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX pet_profile_owner_idx ON pet_profile (owner_id, id);
