ALTER TABLE applications
    ADD COLUMN IF NOT EXISTS email_received_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS proposed_status VARCHAR(255),
    ADD COLUMN IF NOT EXISTS status_proposal_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS status_proposal_received_at TIMESTAMP WITH TIME ZONE;
