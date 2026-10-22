ALTER TABLE applications
    ADD COLUMN IF NOT EXISTS ai_discovered BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requires_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS confidence_score NUMERIC(3,2),
    ADD COLUMN IF NOT EXISTS raw_email_snippet TEXT,
    ADD COLUMN IF NOT EXISTS email_message_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_applications_user_review ON applications(user_id, requires_review);
CREATE INDEX IF NOT EXISTS idx_applications_email_msg ON applications(user_id, email_message_id);
