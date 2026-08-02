CREATE TABLE users (
    id           UUID PRIMARY KEY,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255),
    full_name    VARCHAR(255),
    avatar_url   VARCHAR(255),
    provider     VARCHAR(255) NOT NULL,
    role         VARCHAR(255) NOT NULL,
    provider_id  VARCHAR(255),
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     UUID NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);