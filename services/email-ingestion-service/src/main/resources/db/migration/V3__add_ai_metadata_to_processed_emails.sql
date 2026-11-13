ALTER TABLE processed_emails
    ADD COLUMN IF NOT EXISTS confidence_score NUMERIC(3, 2),
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
