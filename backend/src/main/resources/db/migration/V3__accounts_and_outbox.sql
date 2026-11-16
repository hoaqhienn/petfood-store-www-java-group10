CREATE TABLE user_account (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email_normalized text NOT NULL UNIQUE,
    display_name text NOT NULL,
    password_hash text NOT NULL,
    phone text,
    role text NOT NULL,
    status text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE notification_outbox (
    id uuid PRIMARY KEY,
    type text NOT NULL,
    aggregate_id bigint NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}',
    status text NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz
);
CREATE INDEX notification_outbox_due_idx ON notification_outbox(status, next_attempt_at);
