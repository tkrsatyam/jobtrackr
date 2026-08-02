CREATE TABLE applications (
    application_id   UUID PRIMARY KEY,
    user_id          UUID NOT NULL,
    company_name     VARCHAR(200) NOT NULL,
    role             VARCHAR(200) NOT NULL,
    job_url          VARCHAR(1000),
    status           VARCHAR(255) NOT NULL,
    priority         VARCHAR(255),
    work_mode        VARCHAR(255),
    location         VARCHAR(200),
    salary_min       BIGINT,
    salary_max       BIGINT,
    currency         VARCHAR(10),
    applied_date     DATE,
    source           VARCHAR(255),
    notes            TEXT,
    is_archived      BOOLEAN NOT NULL,
    is_deleted       BOOLEAN NOT NULL,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP
);

CREATE TABLE application_status_history (
    history_id      UUID PRIMARY KEY,
    application_id  UUID NOT NULL REFERENCES applications(application_id),
    status          VARCHAR(255) NOT NULL,
    note            TEXT,
    changed_at      TIMESTAMP
);

CREATE TABLE application_tags (
    id              UUID PRIMARY KEY,
    application_id  UUID NOT NULL REFERENCES applications(application_id),
    tag             VARCHAR(50) NOT NULL,
    UNIQUE(application_id, tag)
);

CREATE TABLE application_documents (
    id              UUID PRIMARY KEY,
    application_id  UUID NOT NULL REFERENCES applications(application_id),
    document_id     UUID NOT NULL,
    linked_at       TIMESTAMP
);

CREATE INDEX idx_applications_user ON applications(user_id);
CREATE INDEX idx_applications_status ON applications(status);
CREATE INDEX idx_applications_user_status ON applications(user_id, status);
CREATE INDEX idx_status_history_app ON application_status_history(application_id);