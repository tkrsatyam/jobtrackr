# 🗄️ JobTrackr — Database Schema

---

## Overview

| Service | Database | Why |
|---|---|---|
| User Service | PostgreSQL | Structured, relational user data |
| Application Service | PostgreSQL | Relational, needs transactions |
| Reminder Service | PostgreSQL | Relational, time-based queries |
| Document Service | PostgreSQL (metadata) + MinIO (files) | Metadata is relational; files are binary blobs |
| Contact Service | MongoDB | Flexible schema per contact type |
| Notification Service | MongoDB | High write volume, append-only, flexible payloads |
| Analytics Service | MongoDB | Aggregation pipelines over event data |

---

## PostgreSQL Schemas

### User Service — `jobtrackr_users` database

> Schema is auto-managed by Hibernate (`ddl-auto=update`). The SQL below reflects what Hibernate generates from the entity classes.

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255),                    -- NULL for OAuth2 accounts
    full_name       VARCHAR(255),
    avatar_url      VARCHAR(255),
    provider        VARCHAR(255) NOT NULL,           -- LOCAL | GOOGLE
    provider_id     VARCHAR(255),                    -- Google subject ID
    role            VARCHAR(255) NOT NULL,           -- USER | ADMIN
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,        -- Plain UUID string, not hashed
    user_id     UUID NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
```

**Not yet implemented — planned for a later phase:**
```sql
-- User preferences (target role, salary range, work mode)
CREATE TABLE user_preferences (
    preference_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_role         VARCHAR(150),
    target_salary_min   BIGINT,
    target_salary_max   BIGINT,
    currency            VARCHAR(10) DEFAULT 'INR',
    preferred_work_mode VARCHAR(20),                 -- REMOTE | HYBRID | ON_SITE
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW()
);

-- Preferred locations (one row per location per user)
CREATE TABLE user_preferred_locations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    location    VARCHAR(100) NOT NULL
);
```

> Account deletion is currently a hard delete. Soft delete (`is_deleted`, `is_active` flags) is a future hardening item.

---

### Application Service — `jobtrackr_applications` database

```sql
CREATE TYPE application_status AS ENUM (
    'SAVED', 'APPLIED', 'PHONE_SCREEN', 'INTERVIEW',
    'TECHNICAL_ROUND', 'HR_ROUND', 'OFFER',
    'ACCEPTED', 'REJECTED', 'GHOSTED', 'WITHDRAWN'
);

CREATE TYPE priority_level AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'DREAM');
CREATE TYPE work_mode AS ENUM ('REMOTE', 'HYBRID', 'ON_SITE');
CREATE TYPE application_source AS ENUM (
    'LINKEDIN', 'NAUKRI', 'INTERNSHALA', 'COMPANY_WEBSITE',
    'REFERRAL', 'ANGEL_LIST', 'INSTAHYRE', 'OTHER'
);

CREATE TABLE applications (
    application_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    company_name    VARCHAR(200) NOT NULL,
    role            VARCHAR(200) NOT NULL,
    job_url         VARCHAR(1000),
    status          application_status NOT NULL DEFAULT 'APPLIED',
    priority        priority_level DEFAULT 'MEDIUM',
    work_mode       work_mode,
    location        VARCHAR(200),
    salary_min      BIGINT,
    salary_max      BIGINT,
    currency        VARCHAR(10) DEFAULT 'INR',
    applied_date    DATE,
    source          application_source DEFAULT 'OTHER',
    notes           TEXT,
    is_archived     BOOLEAN DEFAULT FALSE,
    is_deleted      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE application_status_history (
    history_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES applications(application_id) ON DELETE CASCADE,
    status          application_status NOT NULL,
    note            TEXT,
    changed_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE application_tags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES applications(application_id) ON DELETE CASCADE,
    tag             VARCHAR(50) NOT NULL,
    UNIQUE(application_id, tag)
);

-- Cross-service reference tables (store IDs only, no FK to other services)
CREATE TABLE application_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES applications(application_id) ON DELETE CASCADE,
    document_id     UUID NOT NULL,
    linked_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_applications_user ON applications(user_id);
CREATE INDEX idx_applications_status ON applications(status);
CREATE INDEX idx_applications_user_status ON applications(user_id, status);
CREATE INDEX idx_status_history_app ON application_status_history(application_id);
```

> **Note:** The custom PostgreSQL enum types above reflect the original design intent.
> In practice, `ddl-auto=update` with `@Enumerated(EnumType.STRING)` means Hibernate
> stores all enum fields as `VARCHAR` columns — the custom PG enum types are not created.
> The table structure and column names are accurate; only the column types differ.

> **Timestamp types:** Entities use `LocalDateTime` with `@CreationTimestamp`/`@UpdateTimestamp`. Hibernate maps this to `TIMESTAMP WITHOUT TIME ZONE` — not `TIMESTAMPTZ`. Timestamps in API responses have no timezone offset.

---

### Reminder Service — `reminderservice` database

```sql
CREATE TYPE reminder_type AS ENUM ('FOLLOW_UP', 'INTERVIEW', 'TASK', 'DEADLINE', 'CUSTOM');
CREATE TYPE reminder_status AS ENUM ('PENDING', 'DONE', 'SNOOZED', 'CANCELLED');

CREATE TABLE reminders (
    reminder_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    application_id  UUID,                            -- Optional link to an application
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    type            reminder_type DEFAULT 'CUSTOM',
    due_at          TIMESTAMPTZ NOT NULL,
    status          reminder_status DEFAULT 'PENDING',
    snooze_until    TIMESTAMPTZ,
    is_deleted      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_reminders_user ON reminders(user_id);
CREATE INDEX idx_reminders_due ON reminders(due_at) WHERE status = 'PENDING';
CREATE INDEX idx_reminders_app ON reminders(application_id);
```

---

### Document Service — `documentservice` database

```sql
CREATE TYPE document_type AS ENUM ('RESUME', 'COVER_LETTER', 'OTHER');

CREATE TABLE documents (
    document_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    type            document_type NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    file_size       BIGINT NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,           -- MinIO object key
    is_default      BOOLEAN DEFAULT FALSE,
    is_deleted      BOOLEAN DEFAULT FALSE,
    uploaded_at     TIMESTAMPTZ DEFAULT NOW()
);

-- Ensure only one default resume per user
CREATE UNIQUE INDEX idx_default_resume_per_user
    ON documents(user_id)
    WHERE is_default = TRUE AND type = 'RESUME' AND is_deleted = FALSE;

CREATE INDEX idx_documents_user ON documents(user_id);
```

---

## MongoDB Collections

### Contact Service — `contactservice` database

**Collection: `contacts`**
```json
{
  "_id": "ObjectId",
  "userId": "uuid-string",
  "applicationId": "uuid-string",
  "name": "Priya Menon",
  "role": "RECRUITER",
  "email": "priya@zepto.com",
  "phone": "+91-9876543210",
  "linkedinUrl": "https://linkedin.com/in/priya",
  "company": "Zepto",
  "interactions": [
    {
      "interactionId": "uuid-string",
      "type": "EMAIL",
      "date": "2024-01-18",
      "notes": "Sent follow-up, she replied saying they're reviewing.",
      "createdAt": "2024-01-18T10:00:00Z"
    },
    {
      "interactionId": "uuid-string",
      "type": "CALL",
      "date": "2024-01-20",
      "notes": "Called to confirm interview slot.",
      "createdAt": "2024-01-20T14:00:00Z"
    }
  ],
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-20T14:00:00Z"
}
```

**Indexes:**
```js
db.contacts.createIndex({ userId: 1 })
db.contacts.createIndex({ applicationId: 1 })
db.contacts.createIndex({ userId: 1, name: "text", company: "text" })
```

---

### Notification Service — `notificationservice` database

**Collection: `notifications`**
```json
{
  "_id": "ObjectId",
  "notificationId": "uuid-string",
  "userId": "uuid-string",
  "type": "REMINDER_DUE",
  "title": "Reminder: Follow up with Zepto HR",
  "message": "Your follow-up reminder for Zepto is due today.",
  "read": false,
  "relatedEntityType": "REMINDER",
  "relatedEntityId": "reminder-uuid",
  "applicationId": "application-uuid",
  "createdAt": "2024-01-22T09:00:00Z"
}
```

**Collection: `notification_preferences`**
```json
{
  "_id": "ObjectId",
  "userId": "uuid-string",
  "reminderDue": true,
  "statusChanged": true,
  "weeklySummary": true,
  "documentExpiry": false,
  "emailEnabled": true,
  "inAppEnabled": true,
  "updatedAt": "2024-01-10T00:00:00Z"
}
```

**Indexes:**
```js
db.notifications.createIndex({ userId: 1, read: 1, createdAt: -1 })
db.notification_preferences.createIndex({ userId: 1 }, { unique: true })
```

---

### Analytics Service — `analyticsservice` database

**Collection: `application_events`**
> Stores a copy of every meaningful event, consumed from Kafka.

```json
{
  "_id": "ObjectId",
  "eventId": "uuid-string",
  "userId": "uuid-string",
  "applicationId": "uuid-string",
  "eventType": "STATUS_CHANGED",
  "payload": {
    "fromStatus": "APPLIED",
    "toStatus": "PHONE_SCREEN",
    "companyName": "Zepto",
    "role": "Backend Engineer",
    "source": "NAUKRI",
    "workMode": "HYBRID",
    "location": "Mumbai"
  },
  "occurredAt": "2024-01-18T14:00:00Z"
}
```

Event types: `APPLICATION_CREATED`, `STATUS_CHANGED`, `APPLICATION_DELETED`

**Indexes:**
```js
db.application_events.createIndex({ userId: 1, occurredAt: -1 })
db.application_events.createIndex({ userId: 1, eventType: 1 })
db.application_events.createIndex({ userId: 1, "payload.source": 1 })
```

---

## Redis Usage

| Key Pattern | TTL | Purpose | Status |
|---|---|---|---|
| `blacklist:{token}` | Remaining token lifetime | Access token invalidated on logout | ✅ Implemented |
| `user:profile:{userId}` | 5 min | Cached user profile | ⬜ Planned |
| `analytics:summary:{userId}` | 10 min | Cached dashboard summary | ⬜ Phase 4 |
| `rate:limit:{userId}:{endpoint}` | 1 min | Rate limiting per user per endpoint | ⬜ Planned |

> Only the blacklist key is active today. The refresh token is stored in PostgreSQL only — there is no Redis entry for active refresh tokens.