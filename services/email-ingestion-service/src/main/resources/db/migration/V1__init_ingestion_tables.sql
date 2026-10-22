CREATE TABLE email_polling_state (
    user_id UUID PRIMARY KEY,
    last_history_id VARCHAR(255),
    last_polled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE processed_emails (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    subject TEXT,
    sender TEXT,
    received_at TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_processed_emails_user_message UNIQUE (user_id, message_id)
);

CREATE INDEX idx_processed_emails_user_id ON processed_emails(user_id);
CREATE INDEX idx_processed_emails_message_id ON processed_emails(message_id);
