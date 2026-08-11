CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_applications_company_trgm ON applications USING GIN (lower(company_name) gin_trgm_ops);
CREATE INDEX idx_applications_role_trgm ON applications USING GIN (lower(role) gin_trgm_ops);