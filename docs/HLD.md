# 🏛️ JobTrackr — High Level Design (HLD)

---

## 1. System Overview

JobTrackr is a distributed, event-driven microservices application. Each service owns its data, communicates via REST for synchronous queries and Apache Kafka for asynchronous event propagation.

**Key design goals:**
- **Loose coupling** — services don't call each other for core operations; they react to events
- **Single responsibility** — each service does one thing well
- **Independent deployability** — each service can be deployed and scaled separately
- **Resilience** — failure in one service (e.g. Notification) doesn't break core functionality (Application tracking)

---

## 2. Architecture Style

**Pattern:** Microservices + Event-Driven Architecture (EDA)

**Communication types:**
| Type | When Used | Technology |
|---|---|---|
| Synchronous (request/response) | User-facing reads and writes | REST via API Gateway |
| Asynchronous (fire and forget) | Side effects, notifications, analytics | Apache Kafka |
| Synchronous inter-service | Rare — only where strongly consistent data is needed | OpenFeign (REST) |

---

## 3. Component Breakdown

### 3.1 Client Layer

**Angular SPA** (`frontend/jobtrackr-fe`)
- Built with Angular 21 standalone components, lazy-loaded routes
- Angular Material for UI components
- Angular CDK for drag-and-drop (kanban board)
- Kanban board with JIRA-style column highlighting on drag; terminal statuses exposed as compact drop zones rather than full columns
- `authInterceptor` attaches JWT to every request; handles 401 by silently refreshing the token and retrying the original request
- `authGuard` protects all authenticated routes, redirects to `/login`
- `TokenStorageService` stores tokens in `localStorage`, exposes reactive signals
- Routes: `/login`, `/register`, `/dashboard`, `/applications`, `/applications/board`, `/applications/new`, `/applications/:id`, `/applications/:id/edit`, `/settings`
- Environment-based API URL: `http://localhost:8080` (dev) / `https://jobtrackr-gateway.onrender.com` (prod)
- WebSocket / SSE connection to Notification Service — Phase 3

---

### 3.2 API Gateway (Spring Cloud Gateway — MVC variant)

Built on `spring-cloud-starter-gateway-server-webmvc`, not the reactive/WebFlux gateway — kept consistent with the rest of the stack, which is blocking Spring MVC throughout.

**Currently implemented:**
- Single entry point for all client traffic
- Routes defined as `RouterFunction` beans, split by profile: `GatewayRoutesConfig` (`@Profile("!prod")`) resolves services via Eureka `lb()` locally/Docker; `GatewayRoutesProdConfig` (`@Profile("prod")`) points directly at hardcoded Render URLs from env vars, since Render instances don't share a registry
- JWT validation on every protected route — the Gateway verifies the signature itself using the shared secret (`jjwt`); it does **not** call User Service per request
- Redis lookup for a logout blacklist (`blacklist:<token>`) before letting a validated token through
- Forwards trusted identity downstream as `X-User-Id`, `X-User-Email`, `X-User-Role` headers (extracted from JWT claims) — downstream services trust these headers and never parse JWTs themselves
- CORS handling for the Angular origin

**Planned, not yet implemented:**
- Rate limiting per user (Redis)
- Request logging / tracing header injection (`X-Correlation-Id`)
- SSL termination — currently handled by Render's platform load balancer in production, not application code

**Routing rules (examples):**
```
/api/auth/**         → User Service
/api/users/**        → User Service
/api/applications/** → Application Service
/api/reminders/**    → Reminder Service
/api/documents/**    → Document Service
/api/contacts/**     → Contact Service
/api/notifications/→ Notification Service
/api/analytics/    → Analytics Service
```
> Only the first two rows are live. The rest are defined ahead of time with no service behind them yet.

---

### 3.3 Service Registry (Netflix Eureka)

- Used in local dev and Docker Compose only. Every running service registers on startup; the Gateway resolves `lb://service-name` URIs against it
- Enables multiple instances of the same service (horizontal scaling) — supported by the pattern, not exercised yet
- **Fully disabled in production**, not just routed around: `eureka.client.enabled=false` alone didn't stop it from initializing (boot logs still showed `DiscoveryClientOptionalArgsConfiguration` running). The actual fix excludes the autoconfiguration classes directly (`EurekaClientAutoConfiguration`, `DiscoveryClientOptionalArgsConfiguration`, `AutoServiceRegistrationAutoConfiguration`) on each `@SpringBootApplication` in the prod profile
- Default lease renewal interval is 30s; local eviction timer is tightened to 5s for faster feedback during development

---

### 3.4 User Service

**Owns:** User accounts, authentication, profiles, preferences

**Key flows:**
- Registration → hash password (BCrypt cost 12) → save user → return token pair
  *(publishing `user.registered` Kafka event is planned for Phase 3 — not yet implemented)*
- Login → validate credentials → issue JWT access token + refresh token → store refresh token in PostgreSQL
- Token refresh → validate refresh token → rotate (old revoked, new issued) → return new token pair
- Logout → blacklist access token in Redis until natural expiry → revoke current session's refresh token in PostgreSQL only (other sessions unaffected)
- Account deletion → hard-delete all refresh tokens first (FK constraint) → delete user

**JWT payload:**
```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "role": "USER",
  "iat": 1705312800,
  "exp": 1705316400
}
```

**Token storage:**
- Access token: 15 minutes (production), 24 hours (development). On logout, blacklisted in Redis as `blacklist:{token}` with TTL = remaining token lifetime.
- Refresh token: 7 days. Stored as a plain UUID string in PostgreSQL with a `revoked` boolean. **Not hashed, not stored in Redis.** Rotated on every use — old token revoked, new one issued.
- Per-session logout only — logout does not affect other active sessions.

**Prod profile (`application-prod.properties`):**
- Eureka fully disabled via properties (`eureka.client.enabled=false`, `eureka.client.register-with-eureka=false`, `eureka.client.fetch-registry=false`, `spring.cloud.discovery.enabled=false`)
- Redis configured via `spring.data.redis.url=${REDIS_URL}` (Upstash `rediss://` URL format)
- SQL logging disabled (`spring.jpa.show-sql=false`)
- Access token TTL restored to 900000ms (15 min) — overrides the dev value in `application.properties`

**`/ping` endpoint:** exposed for Render keep-warm pings (`GET /ping` → `200 "User service: pong"`)

---

### 3.5 Application Service

**Owns:** Job application records, status history, tags, document references (UUID only)

**Key flows:**
- Create application → build entity with defaults → add initial history entry via cascade → save to PostgreSQL → publish `application.created` event (currently logged only — Kafka wired in Phase 3)
- Status change → validate transition via `StatusTransitionValidator` → append history entry to collection via cascade → save → publish `application.status.updated` event
- Delete → soft delete (`is_deleted = true`) → publish `application.deleted` event
- Archive → toggle `is_archived` flag
- Tag management → add/remove tags per application (stored lowercase, displayed title-case on frontend)
- Bulk operations → bulk delete, bulk archive, bulk status change (invalid transitions silently skipped per item)
- Queries → JPA Specifications for dynamic filtering, Spring Data pagination

**Does NOT know about:** Document metadata, contacts, reminders. Stores only their UUIDs as cross-service references in `application_documents` table.

**Document enrichment:** `DocumentServiceClient` (OpenFeign) calls Document Service to enrich responses with document metadata. Falls back to empty list if Document Service is unavailable — core application tracking is never blocked by Document Service being down.

**Kafka producer:** `KafkaProducerConfig` is annotated `@Profile("!prod")` — not active in production yet. `ApplicationEventProducer` logs events only (send calls commented out). Full Kafka wiring happens in Phase 3.

**`/ping` endpoint:** `GET /ping` → `200 "Application service: pong"` — used for Render keep-warm pings.

**Prod profile (`application-prod.properties`):**
- Eureka fully disabled via properties
- Database via `${DB_URL}` (Neon PostgreSQL)
- Redis via `${REDIS_URL}` (Upstash `rediss://` URL format)
- Kafka intentionally omitted — `KafkaProducerConfig` excluded via `@Profile("!prod")`, log-only stub handles prod fine until Phase 3
- SQL logging disabled
- Log level set to INFO

---

### 3.6 Reminder Service

**Owns:** Reminders and their states

**Scheduled job (Spring @Scheduled):**
- Every minute, query reminders where `due_at <= NOW()` and `status = PENDING`
- For each due reminder, publish `reminder.triggered` event to Kafka
- Update status to `FIRED` to prevent duplicate triggers

---

### 3.7 Document Service

**Owns:** Document metadata in PostgreSQL; binary files in MinIO

**Key flows:**
- Upload → receive multipart file → validate type/size → upload to MinIO with generated key → save metadata to PostgreSQL → publish `document.uploaded` event
- Preview → generate pre-signed MinIO URL (valid 1 hour) → return to client
- Versioning → each upload creates a new record; version number auto-incremented per user per document type

**MinIO storage key pattern:**
```
documents/{userId}/{documentType}/{documentId}/{fileName}
```

---

### 3.8 Contact Service

**Owns:** Contact records and interaction logs (MongoDB)

**Why MongoDB here:**
- Contact schema varies — some contacts have LinkedIn, some have phone, some have both
- Interaction log is a naturally embedded sub-array
- No cross-contact JOINs needed

---

### 3.9 Notification Service

**Owns:** Notification records, delivery, and preferences (MongoDB)

**Kafka consumer groups:**
- Consumes from: `application.status.updated`, `reminder.triggered`, `user.registered`
- On consume → check user preferences → create notification record → deliver via email and/or WebSocket

**Email delivery:**
- Development: JavaMailSender with local MailHog SMTP
- Production: SendGrid API/Resend API

**Real-time delivery:**
- WebSocket endpoint: `/ws/notifications`
- Client subscribes on login; server pushes new notifications as they arrive

---

### 3.10 Analytics Service

**Owns:** Aggregated event data (MongoDB)

**Kafka consumer:**
- Subscribes to `application.created`, `application.status.updated`, `application.deleted`
- Writes events to `application_events` collection

**Query approach:**
- Dashboard summary: MongoDB aggregation pipeline over events
- Timeline: `$group` by date bucket
- Funnel: `$match` + `$group` on status transitions
- Results cached in Redis for 10 minutes per user

---

## 4. Kafka Topics

| Topic | Producer | Consumers | Payload |
|---|---|---|---|
| `user.registered` | User Service | Notification Service | userId, name, email |
| `application.created` | Application Service | Analytics Service | full application snapshot |
| `application.status.updated` | Application Service | Notification Service, Analytics Service | applicationId, userId, fromStatus, toStatus, companyName |
| `application.deleted` | Application Service | Analytics Service | applicationId, userId |
| `reminder.triggered` | Reminder Service | Notification Service | reminderId, userId, applicationId, title |
| `document.uploaded` | Document Service | Application Service | documentId, userId, applicationId (if linked) |

**Configuration:**
- Partitions: 3 per topic (can scale later)
- Replication factor: 1 (local dev), 3 (production)
- Consumer groups: each consuming service has its own group ID
- Offset management: earliest (don't miss events on restart)

---

## 5. Data Flow — Key Scenarios

### 5.1 User Creates an Application
```
Angular → POST /api/applications → API Gateway
    → validates JWT
    → routes to Application Service
        → saves to PostgreSQL
        → publishes to Kafka: application.created
            → Analytics Service consumes → stores event in MongoDB
    → returns 201 to Angular
```

### 5.2 Status Changes to "Interview"
```
Angular → PUT /api/applications/{id}/status → API Gateway
    → Application Service
        → appends status_history record
        → publishes to Kafka: application.status.updated
            → Notification Service consumes
                → checks user preferences
                → saves notification to MongoDB
                → sends email via SendGrid/Resend
                → pushes via WebSocket to connected client
            → Analytics Service consumes
                → updates event store
    → returns 200 to Angular
```

### 5.3 Reminder Fires
```
Reminder Service scheduler (every 60s)
    → queries PostgreSQL for due reminders
    → publishes to Kafka: reminder.triggered
        → Notification Service consumes
            → creates in-app notification
            → sends email if preference enabled
            → pushes via WebSocket
    → marks reminder status as FIRED
```

---

## 6. Security Design

| Concern | Approach |
|---|---|
| Authentication | JWT Bearer tokens |
| Token storage (client) | `localStorage` — access token and refresh token stored after login. HttpOnly cookies are the more secure alternative (immune to XSS) but require backend changes to set/read cookies; noted as a future hardening item |
| Token expiry | Access: 15 min, Refresh: 7 days |
| Token revocation | Redis blacklist on logout |
| Gateway → downstream trust | Gateway forwards `X-User-Id`, `X-User-Email`, `X-User-Role` headers after validating the JWT itself; downstream services trust these and skip JWT handling entirely |
| Service-to-service auth (OpenFeign) | Not implemented yet — no service currently calls another directly; planned once Document/Contact services exist and Application Service needs to reach them |
| Resource ownership | Every service checks `userId` from JWT matches resource owner |
| HTTPS | Enforced at API Gateway in production |
| Password hashing | BCrypt (cost factor 12) |
| File uploads | Type validation, size limit (5MB), stored with opaque keys in MinIO |

---

## 7. Caching Strategy

| What | Where | TTL | Invalidation |
|---|---|---|---|
| JWT blacklist | Redis | Until token expiry | N/A (TTL-based) |
| User profile | Redis | 5 min | On profile update |
| Analytics summary | Redis | 10 min | On new application event |
| Document pre-signed URL | Client-side | 1 hour | Not cached server-side |

---

## 8. Local Development Architecture

Target architecture once all phases are built. What's actually defined in today's `docker-compose.yml` is marked below:

Infrastructure containers:
- postgres        (port 5432)   ✅ running today
- redis           (port 6379)   ✅ running today
- mongodb         (port 27017)  ⬜ Phase 2 (Contact Service)
- kafka           (port 9092)   ⬜ Phase 3
- zookeeper       (port 2181)   ⬜ Phase 3
- minio           (port 9000, console 9001)  ⬜ Phase 2 (Document Service)
- mailhog         (SMTP 1025, UI 8025)        ⬜ Phase 3 (Notification Service)

Application containers:
- eureka-server        (port 8761)  ✅ running today
- api-gateway          (port 8080)  ✅ running today
- user-service         (port 8081)  ✅ running today
- application-service  (port 8082)  ✅ running today
- reminder-service     (port 8083)  ⬜ Phase 2
- document-service     (port 8084)  ⬜ Phase 2
- contact-service      (port 8085)  ⬜ Phase 2
- notification-service (port 8086)  ⬜ Phase 3
- analytics-service    (port 8087)  ⬜ Phase 4

Frontend:
- angular dev server  (port 4200)  ✅ running today

---

## 9. LLD Highlights (Key Design Decisions)

### 9.1 Application Service — Status Machine

Valid transitions are enforced in a `StatusTransitionValidator`:

```
SAVED           → APPLIED, WITHDRAWN
APPLIED         → PHONE_SCREEN, INTERVIEW, REJECTED, GHOSTED, WITHDRAWN
PHONE_SCREEN    → INTERVIEW, TECHNICAL_ROUND, REJECTED, GHOSTED, WITHDRAWN
INTERVIEW       → TECHNICAL_ROUND, HR_ROUND, OFFER, REJECTED, GHOSTED, WITHDRAWN
TECHNICAL_ROUND → HR_ROUND, OFFER, REJECTED, GHOSTED, WITHDRAWN
HR_ROUND        → OFFER, REJECTED, GHOSTED, WITHDRAWN
OFFER           → ACCEPTED, REJECTED, WITHDRAWN
```

Terminal states: `ACCEPTED, REJECTED, GHOSTED, WITHDRAWN`

### 9.2 Reminder Service — Deduplication

Scheduler uses an optimistic lock approach:
- Update `status = 'FIRING'` where `status = 'PENDING' AND due_at <= NOW()`
- Only publish events for rows successfully updated
- Prevents duplicate events on multi-instance deployment

### 9.3 Analytics Service — Event Sourcing Lite

Analytics doesn't query Application Service. Instead it maintains its own read model from Kafka events — a pattern called CQRS (Command Query Responsibility Segregation). This means:
- Analytics queries never slow down the Application Service
- Historical data survives even if the Application Service is down
- Easy to rebuild analytics by replaying Kafka from offset 0

---

## 10. Deployment Architecture

### 10.1 Local Development
All 9 services + all infrastructure run via Docker Compose on your machine. See `docker-compose.yml` in the repo root.
> Application Service uses the same prod Eureka disable pattern as User Service — properties-based (`eureka.client.enabled=false` etc.) since it has no `@Profile` on the main class. Kafka is additionally excluded via `@Profile("!prod")` on `KafkaProducerConfig`.

### 10.2 Production — Free Tier

This project targets a fully free deployment. The strategy is to deploy only the 4 most demo-relevant services and keep the rest local-only, transparently documented.

**Deployed services (Render free web services), Phase 1 scope:**

| Service             | Platform | URL                                          |
|---------------------|---|----------------------------------------------|
| Angular Frontend    | Vercel | `jobtrackr-portal.vercel.app`                 |
| API Gateway         | Render | `jobtrackr-gateway.onrender.com`             |
| User Service        | Render | `jobtrackr-user-service.onrender.com`        |
| Application Service | Render | `jobtrackr-application-service.onrender.com` |

Analytics doesn't exist yet (Phase 4). Reminder/Document/Contact/Notification are built locally only, pending later phases.

**Managed infrastructure (all free tiers):**

| Infrastructure | Provider | Free Limit |
|---|---|---|
| PostgreSQL | Neon | 512MB, always free |
| MongoDB | MongoDB Atlas | 512MB, always free |
| Redis | Upstash | 10k commands/day, always free |
| Kafka | Upstash Kafka | 10k messages/day, always free |
| File storage | Cloudflare R2 | 10GB, always free |
| Email | Resend | 3000 emails/month, always free |
| CI/CD | GitHub Actions | Free for public repos |

**Render free tier behaviour:**
Render free services spin down after 15 minutes of inactivity. Measured cold-start on this stack is 145–166 seconds (heavier than typical, likely free-tier CPU throttling against this many initializing Spring beans), not the ~30s often reported for lighter apps. A GitHub Actions cron pings all three services every 10 min on weekdays during active hours to avoid this in practice; gateway timeouts are set to 180s as a fallback for genuinely cold requests outside that window.

**Kafka in production:**
The deployed services use **Upstash Kafka** which is HTTP-based. Spring Boot connects via the standard Kafka client — only the bootstrap server URL and credentials change between local and prod (environment variables). No code changes needed.

**Local-only services (documented in README):**
Reminder, Document, Contact, and Notification services run in local dev only. This is a deliberate free-tier constraint, stated transparently. The architecture supports deploying them — it's purely a hosting budget decision.

### 10.3 CI/CD Pipeline (GitHub Actions)

```
Push to main branch
    → GitHub Actions triggers
        → Build + test each service (mvn test)
        → Build Docker images
        → Push to Docker Hub
        → Trigger Render deploy via webhook
    → Vercel auto-deploys Angular on push (zero config)
```

Each service has its own `Dockerfile`. Render is pointed at each service's Docker image on Docker Hub.

### 10.4 Environment Variables Strategy

All config is externalised via environment variables. Same Docker image runs locally and in production — only the `.env` changes.

```
Local:  docker-compose.yml sets env vars pointing to local containers
Prod:   Render dashboard sets env vars pointing to managed services
```

Key variables per service:
```
# Shared across all services
EUREKA_URI=http://eureka:8761/eureka          # local
# Eureka fully disabled in prod (autoconfiguration excluded, not just
# eureka.client.enabled=false, which didn't fully suppress it) —
# Gateway routes via hardcoded Render URLs instead

# User / Application / Reminder / Document services
POSTGRES_URL=jdbc:postgresql://...
POSTGRES_USER=...
POSTGRES_PASSWORD=...

# Contact / Notification / Analytics services
MONGODB_URI=mongodb+srv://...

# All services
REDIS_HOST=...
REDIS_PORT=...
REDIS_PASSWORD=...

# API Gateway
JWT_SECRET=...

# Notification Service
KAFKA_BOOTSTRAP_SERVERS=...         # localhost:9092 local / Upstash URL prod
RESEND_API_KEY=...                  # prod only

# Document Service
R2_ACCOUNT_ID=...                   # Cloudflare R2 (prod) / MinIO URL (local)
R2_ACCESS_KEY=...
R2_SECRET_KEY=...
R2_BUCKET=jobtrackr-documents
```

---

## 11. Scaling Considerations

> Note: These are architectural considerations for if this project grew beyond a personal portfolio tool. At current scale (single user, free tier), none of these apply.

| Concern | Approach |
|---|---|
| Application Service slow under load | PostgreSQL read replicas; cache list queries in Redis |
| Notification email queue backs up | Scale Kafka consumer group instances; use Resend batch API |
| Analytics queries slow on large datasets | Pre-aggregate daily stats via scheduled job; use MongoDB `$facet` |
| Document uploads cause memory spikes | Stream multipart uploads directly to R2/MinIO, don't buffer in memory |
| Analytics hit simultaneously by many users | Aggressive Redis caching per userId; background refresh on TTL expiry |
| Reminder scheduler fires duplicates | Distributed lock via Redis (Redisson) before processing batch |
