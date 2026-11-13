CREATE INDEX IF NOT EXISTS idx_processed_emails_user_received
    ON processed_emails (user_id, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_processed_emails_user_status_received
    ON processed_emails (user_id, status, received_at DESC);
