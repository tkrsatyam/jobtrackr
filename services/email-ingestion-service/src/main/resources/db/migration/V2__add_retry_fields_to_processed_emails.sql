ALTER TABLE processed_emails
    ADD COLUMN body_text TEXT,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error TEXT;

CREATE INDEX idx_processed_emails_retry ON processed_emails(user_id, status, retry_count);
