-- Opt-in tracking and personalization (BR24-BR26). Nothing is recorded before consent; email, address and
-- raw search text are never stored. Rows of a deleted account go with it.
CREATE TABLE consent_preference (
    subject_key text PRIMARY KEY,
    user_id bigint REFERENCES user_account(id) ON DELETE CASCADE,
    analytics boolean NOT NULL,
    personalization boolean NOT NULL,
    policy_version text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE recommendation_impression (
    impression_id uuid PRIMARY KEY,
    recommendation_id uuid NOT NULL,
    subject_key text NOT NULL,
    anon_token text NOT NULL,
    user_id bigint REFERENCES user_account(id) ON DELETE CASCADE,
    product_id bigint REFERENCES product(id) ON DELETE SET NULL,
    position integer NOT NULL,
    personalized boolean NOT NULL,
    is_demo boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX recommendation_impression_created_idx ON recommendation_impression (created_at);
CREATE INDEX recommendation_impression_subject_idx ON recommendation_impression (subject_key);

CREATE TABLE behavior_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id uuid NOT NULL UNIQUE,
    name text NOT NULL,
    subject_key text NOT NULL,
    anon_token text NOT NULL,
    user_id bigint REFERENCES user_account(id) ON DELETE CASCADE,
    product_id bigint REFERENCES product(id) ON DELETE SET NULL,
    impression_id uuid,
    metadata jsonb NOT NULL DEFAULT '{}',
    client_time timestamptz,
    server_received_at timestamptz NOT NULL DEFAULT now(),
    is_demo boolean NOT NULL DEFAULT false
);
CREATE INDEX behavior_event_received_idx ON behavior_event (server_received_at);
CREATE INDEX behavior_event_subject_idx ON behavior_event (subject_key, server_received_at);
CREATE INDEX behavior_event_impression_idx ON behavior_event (impression_id) WHERE impression_id IS NOT NULL;
