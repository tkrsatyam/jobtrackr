# 🚀 JobTrackr — Deployment Guide (Free Tier)

> Total monthly cost: $0. All platforms used have genuinely free tiers with no credit card required or trial expiry (except Render, noted below).

---

## Overview

| Environment | How it runs                                                                                   |
|---|-----------------------------------------------------------------------------------------------|
| **Local dev** | Eureka, Gateway, User Service, Application Service + Postgres/Redis via Docker Compose — the other 5 services don't exist yet |
| **Production** | Three Phase 1 services on Render + Angular on Vercel + managed cloud services (Neon, Upstash) |

---

## What Gets Deployed vs Stays Local

Eureka, User Service, Application Service, and API Gateway are fully built and run locally. All three backend services (Gateway, User, Application) are also deployed live on Render — comfortably within the 4-service free tier limit today. That ceiling becomes a real constraint only once Reminder, Document, Contact, and Notification exist in Phase 2–3, at which point some will stay local-only by deliberate choice.

| Service | Local | Deployed | Notes |
|---|---|---|---|
| API Gateway | ✅ | ✅ | Must be live — single entry point |
| User Service | ✅ | ✅ | Must be live — handles auth |
| Application Service | ✅ | ✅ | Core feature |
| Reminder, Document, Contact, Notification, Analytics | ❌ not built yet | ❌ | Phase 2–4, not built |
---

## Platform Accounts to Create

Sign up for these before starting deployment (all free, no credit card except Render):

- [x] [Vercel](https://vercel.com) — Angular frontend
- [x] [Render](https://render.com) — Spring Boot services *(free tier, no credit card needed)*
- [x] [Neon](https://neon.tech) — PostgreSQL
- [x] [Upstash](https://upstash.com) — Redis (Kafka not provisioned yet — Phase 3)
- [x] [Docker Hub](https://hub.docker.com) — Docker image registry
- [x] [GitHub](https://github.com) — Source + CI/CD via Actions

MongoDB Atlas, Cloudflare R2, and Resend are needed for Contact/Document/Notification services — not set up yet since those services aren't built.

---

## Step-by-Step Deployment

### Step 1 — Set Up Managed Infrastructure

**PostgreSQL on Neon**
1. Create a Neon project
2. Create two databases inside it: `jobtrackr_users`, `jobtrackr_applications`
3. Copy each database's connection string (they differ only in the database name at the end of the URL)

**Redis on Upstash**
1. Create a Redis database (free tier)
2. Copy the `rediss://` connection URL — note the double-s, it's TLS
3. Same Redis instance is shared by Gateway, User Service, and Application Service

---

### Step 2 — Deploy to Render

Create 3 Render web services — Gateway, User Service, Application Service — deploying from Docker Hub images. Create User Service and Application Service first; Gateway needs their URLs as env vars, which don't exist until those two are live.

For each service:
1. Connect to the Docker Hub image (`youruser/jobtrackr-{service}:latest`)
2. Set **Instance type** to Free
3. Add `SPRING_PROFILES_ACTIVE=prod` plus the service's env vars (see Environment Variables Reference below)
4. Deploy and copy the assigned `.onrender.com` URL

**Service URLs after deploy (save these):**
```
jobtrackr-gateway              → https://jobtrackr-gateway.onrender.com
jobtrackr-user-service         → https://jobtrackr-user-service.onrender.com
jobtrackr-application-service  → https://jobtrackr-application-service.onrender.com
```

**Eureka in production:**

Eureka is fully disabled in prod, not just routed around. `eureka.client.enabled=false` alone did **not** suppress it — confirmed by boot logs still showing `DiscoveryClientOptionalArgsConfiguration` and LoadBalancer initialization. The actual fix is excluding the autoconfiguration classes directly on each `@SpringBootApplication`:

```java
@SpringBootApplication(exclude = {
    UserDetailsServiceAutoConfiguration.class,
    EurekaClientAutoConfiguration.class,
    DiscoveryClientOptionalArgsConfiguration.class,
    AutoServiceRegistrationAutoConfiguration.class
})
```

The Gateway's routing is split into two profile-scoped `RouterFunction` config classes instead of one property-based config:
1. `GatewayRoutesConfig` (`@Profile("!prod")`) — original Eureka + `lb("service-name")` routing, used locally and in Docker
2. `GatewayRoutesProdConfig` (`@Profile("prod")`) — uses `HandlerFunctions.https()` + `.before(BeforeFilterFunctions.uri(...))` pointed directly at the Render URLs read from env vars

> ⚠️ Render free services sleep after 15 min of inactivity. **Measured cold-start on this stack is 145–166 seconds** — much higher than the ~30s typically reported for lighter apps, likely due to free-tier CPU throttling against this many Spring beans (JPA, Security, LoadBalancer) initializing. Gateway timeouts (`socket-timeout=180s`) are sized to that measured number. A `keep-warm.yml` GitHub Actions workflow pings all three services every 10 minutes, weekdays 10:30 AM–5:00 PM IST, to avoid hitting this during active hours — trigger it manually via `workflow_dispatch` before anything time-sensitive, like an interview.

---

### Step 3 — Deploy Angular to Vercel

1. Push your Angular repo to GitHub
2. Import the repo on Vercel
3. Set **Root Directory** to `frontend/jobtrackr-fe` — the repo root isn't the Angular project root
4. Set **Framework Preset** to Angular (should auto-detect once Root Directory is set)
5. Set **Build Command** to `npm run build` (already builds in production mode — `production` is the default configuration)
6. Set **Output Directory** to `dist/jobtrackr-fe/browser` — note the extra `/browser` nesting; the newer esbuild-based Angular builder outputs one level deeper than older Angular CLI versions
   > This extra `/browser` nesting is the Angular 21 esbuild builder output structure — if this build fails to serve correctly on Vercel, verify the dist output locally with `npm run build` and confirm the path.
7. No environment variables needed — the prod API URL is baked in at build time, not read from a Vercel env var
8. Deploy — Vercel assigns a `*.vercel.app` domain
9. Set **Ignored Build Step** to "Only build if there are changes in a folder," pointed at `frontend/jobtrackr-fe`, so backend/workflow-only commits don't trigger a frontend rebuild
10. Add the new Vercel domain to the Gateway's `ALLOWED_ORIGINS` env var and redeploy the Gateway — skipping this causes CORS failures, not auth failures, which looks like a different bug than it is

In `environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  apiUrl: 'https://jobtrackr-gateway.onrender.com'
};
```

In `angular.json`, under the `production` build configuration:
```json
"fileReplacements": [
  {
    "replace": "src/environments/environment.ts",
    "with": "src/environments/environment.prod.ts"
  }
]
```

Without this, the prod build silently keeps using `environment.ts` (`localhost:8080`) regardless of the build configuration flag — verify the swap actually worked by grepping the built JS for the real URL before trusting it.

---

### Step 4 — Set Up CI/CD with GitHub Actions

Create `.github/workflows/deploy.yml`:

1. A `detect-changes` job runs `dorny/paths-filter` against `services/api-gateway/**`, `services/user-service/**`, `services/application-service/**`, producing a true/false output per service
2. Three independent jobs (`build-gateway`, `build-user-service`, `build-application-service`) each declare `needs: detect-changes` and `if: needs.detect-changes.outputs.<service> == 'true'` — only services that actually changed rebuild
3. Each job: checks out code, builds the jar with Maven, sets up Docker Buildx (`docker/setup-buildx-action` — **required** before using `cache-from/to: type=gha`, since the default `docker` driver doesn't support that cache backend and fails the build), logs into Docker Hub, builds + pushes the image, then curls that service's Render deploy hook

**GitHub Secrets to add:**
```
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
RENDER_GATEWAY_HOOK      ← Deploy hook URL from Render dashboard
RENDER_USER_HOOK
RENDER_APP_HOOK
```

Create `.github/workflows/keep-warm.yml`:
1. Same `needs`-based fan-out pattern — a `check-window` job determines if the current time is inside the active window, then three independent ping jobs run in parallel (not sequentially) if so
2. Scheduled via cron for weekday business hours (10:30 AM–5:00 PM IST), plus `workflow_dispatch` for manual triggering
3. Pings each service's own `/ping` endpoint directly — bypasses the Gateway, since each service has its own independent 15-minute sleep timer

Vercel auto-deploys on push with no extra config needed, aside from the Ignored Build Step folder filter set up in Step 3.

---

## Environment Variables Reference

### API Gateway
```env
SPRING_PROFILES_ACTIVE=prod
USER_SERVICE_URL=https://jobtrackr-user-service.onrender.com
APPLICATION_SERVICE_URL=https://jobtrackr-application-service.onrender.com
JWT_SECRET=your-256-bit-secret
REDIS_URL=rediss://default:password@upstash-host:6379
ALLOWED_ORIGINS=http://localhost:4200,https://<your-vercel-domain>
```

### User Service
```env
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<neon-host>/jobtrackr_users?sslmode=require
DB_USERNAME=neondb_owner
DB_PASSWORD=...
JWT_SECRET=your-256-bit-secret          # Must match Gateway exactly
REDIS_URL=rediss://default:<password>@<upstash-host>:6379
```

> `REDIS_URL` uses Upstash's full `rediss://` URL format — the User Service prod profile reads it via `spring.data.redis.url` (not host/port separately).
> `DB_URL` must not embed credentials in the URL. Neon's default connection string includes `username:password@host` — strip those out and pass them as `DB_USERNAME`/`DB_PASSWORD` separately, otherwise the JDBC driver rejects the URL.

### Application Service
```env
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<neon-host>/jobtrackr_applications?sslmode=require
DB_USERNAME=...
DB_PASSWORD=...
REDIS_URL=rediss://default:password@upstash-host:6379
```
Kafka intentionally omitted — not provisioned yet (Phase 3). `KafkaProducerConfig` is `@Profile("!prod")`; the event producer logs only, doesn't publish.

### Analytics Service
```env
MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/jobtrackr
KAFKA_BOOTSTRAP_SERVERS=pkc-xxx.upstash.io:9092
KAFKA_SASL_USERNAME=...
KAFKA_SASL_PASSWORD=...
REDIS_URL=rediss://default:password@upstash-host:6379
```

---

## Spring Boot Profile Strategy

Use Spring profiles to switch between local and prod config cleanly:

```
src/main/resources/
  application.properties           ← shared/local config (uses .properties, not .yml, across this project)
  application-docker.properties    ← Docker Compose overrides
  application-prod.properties      ← prod overrides (Neon, Upstash, hardcoded Render URLs)
```

Run locally with: `--spring.profiles.active=local`
Render sets: `SPRING_PROFILES_ACTIVE=prod`

---

## Verifying the Deployment

After all services are up, verify end-to-end:

```bash
# 1. Health check gateway (and the other two services)
curl https://jobtrackr-gateway.onrender.com/ping
curl https://jobtrackr-user-service.onrender.com/ping
curl https://jobtrackr-application-service.onrender.com/ping

# 2. Register a user
curl -X POST https://jobtrackr-gateway.onrender.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Test","email":"test@example.com","password":"Test@1234"}'

# 3. Login
curl -X POST https://jobtrackr-gateway.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test@1234"}'

# 4. Create an application (use token from step 3)
curl -X POST https://jobtrackr-gateway.onrender.com/api/applications \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"companyName":"Google","role":"SWE","status":"APPLIED"}'
```

---

## Demo Tips for Resume / Interviews

Since Render free services on this stack cold-start in 2–3 minutes (not the ~30s typical of lighter apps), do this before a demo or sharing the link:

1. Hit the health check endpoint to wake all services up
2. Have a pre-seeded demo account ready with 20–30 applications across different statuses so the analytics charts look good
3. Record a **Loom video** (2–3 min) as a backup — more reliable than a live demo with a cold-start risk
4. Pin the Loom link in your GitHub README alongside the live URL
