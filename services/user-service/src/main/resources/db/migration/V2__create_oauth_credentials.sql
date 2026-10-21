CREATE TABLE oauth_credentials (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255),
    email            VARCHAR(255),
    access_token     TEXT,
    refresh_token    TEXT,
    expires_at       TIMESTAMP,
    scopes           TEXT,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT uk_oauth_credentials_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_oauth_credentials_user_id ON oauth_credentials(user_id);
