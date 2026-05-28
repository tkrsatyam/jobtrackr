# 🚀 JobTrackr — Deployment Guide (Free Tier)

> Total monthly cost: $0. All platforms used have genuinely free tiers with no credit card required or trial expiry (except Render, noted below).

---

## Overview

| Environment | How it runs |
|---|---|
| **Local dev** | All 9 services + infrastructure via Docker Compose |
| **Production** | 4 core services on Render + Angular on Vercel + managed cloud services |

---

## What Gets Deployed vs Stays Local

All 8 backend services are fully built and run locally. Only 4 are deployed live — constrained by Render's free tier (4 free web services). This is documented transparently and is a deliberate decision, not a gap in the architecture.

| Service | Local | Deployed | Notes |
|---|---|---|---|
| API Gateway | ✅ | ✅ | Must be live — single entry point |
| User Service | ✅ | ✅ | Must be live — handles auth |
| Application Service | ✅ | ✅ | Core feature |
| Analytics Service | ✅ | ✅ | Showcases MongoDB + Kafka + charts |
| Reminder Service | ✅ | ❌ | Fully built, free tier limit |
| Document Service | ✅ | ❌ | Fully built, free tier limit |
| Contact Service | ✅ | ❌ | Fully built, free tier limit |
| Notification Service | ✅ | ❌ | Fully built, free tier limit |

---

## Platform Accounts to Create

Sign up for these before starting deployment (all free, no credit card except Render):

- [ ] [Vercel](https://vercel.com) — Angular frontend
- [ ] [Render](https://render.com) — Spring Boot services *(free tier, no credit card needed)*
- [ ] [Neon](https://neon.tech) — PostgreSQL
- [ ] [MongoDB Atlas](https://cloud.mongodb.com) — MongoDB
- [ ] [Upstash](https://upstash.com) — Redis + Kafka
- [ ] [Cloudflare](https://cloudflare.com) — R2 file storage *(requires free account, no card for R2)*
- [ ] [Resend](https://resend.com) — Email delivery
- [ ] [Docker Hub](https://hub.docker.com) — Docker image registry
- [ ] [GitHub](https://github.com) — Source + CI/CD via Actions

---

## Step-by-Step Deployment

### Step 1 — Set Up Managed Infrastructure

**PostgreSQL on Neon**
1. Create a Neon project → create a database named `jobtrackr`
2. Copy the connection string: `postgresql://user:pass@host/jobtrackr?sslmode=require`
3. Run schema migrations for User, Application, Reminder, Document services

**MongoDB on Atlas**
1. Create a free M0 cluster (512MB)
2. Create a database user with read/write access
3. Whitelist all IPs (`0.0.0.0/0`) for Render's dynamic IPs
4. Copy connection string: `mongodb+srv://user:pass@cluster.mongodb.net/jobtrackr`

**Redis on Upstash**
1. Create a Redis database (free tier)
2. Copy the REST URL and token, or the Redis connection URL

**Kafka on Upstash**
1. Create a Kafka cluster (free tier)
2. Create topics: `application.created`, `application.status.updated`, `application.deleted`, `reminder.triggered`, `document.uploaded`, `user.registered`
3. Copy bootstrap server URL, username, and password

**File Storage on Cloudflare R2**
1. Create an R2 bucket named `jobtrackr-documents`
2. Create an API token with read/write access
3. Note your Account ID, Access Key, and Secret Key
4. R2 is S3-compatible — the Document Service uses the S3 SDK pointed at `https://<account-id>.r2.cloudflarestorage.com`

**Email on Resend**
1. Create a Resend account
2. Add and verify a sending domain (or use their sandbox for testing)
3. Copy your API key

---

### Step 2 — Prepare Docker Images

Each Spring Boot service needs a `Dockerfile`. Example for any service:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and push to Docker Hub:
```bash
# From each service directory
mvn clean package -DskipTests
docker build -t youruser/jobtrackr-gateway:latest .
docker push youruser/jobtrackr-gateway:latest

# Repeat for user-service, application-service, analytics-service
```

---

### Step 3 — Deploy to Render

Create a **Web Service** on Render for each of the 4 deployed services. For each:

1. Connect your GitHub repo (or use Docker Hub image directly)
2. Set **Environment** to Docker
3. Set the **Docker image** to `youruser/jobtrackr-{service}:latest`
4. Add all environment variables (see Environment Variables section below)
5. Set **Instance type** to Free

**Service URLs after deploy (save these):**
```
jobtrackr-gateway     → https://jobtrackr-gateway.onrender.com
jobtrackr-user        → https://jobtrackr-user.onrender.com
jobtrackr-app         → https://jobtrackr-app.onrender.com
jobtrackr-analytics   → https://jobtrackr-analytics.onrender.com
```

> ⚠️ Render free services sleep after 15 min of inactivity. First request after sleep takes ~30 seconds to cold-start. This is expected and acceptable for a portfolio project.

**Eureka in production:**
Render services can't discover each other via Eureka the same way Docker containers can. In production, replace Eureka-based discovery in the API Gateway with hardcoded Render URLs as environment variables. The Gateway routing config becomes:

```yaml
# application-prod.yml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: ${USER_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/auth/**, /api/users/**
        - id: application-service
          uri: ${APPLICATION_SERVICE_URL:http://localhost:8082}
          predicates:
            - Path=/api/applications/**
```

Set `USER_SERVICE_URL`, `APPLICATION_SERVICE_URL`, `ANALYTICS_SERVICE_URL` as environment variables on Render pointing to the respective Render URLs.

---

### Step 4 — Deploy Angular to Vercel

1. Push your Angular repo to GitHub
2. Import the repo on Vercel
3. Set **Framework Preset** to Angular
4. Set **Build Command** to `ng build --configuration production`
5. Set **Output Directory** to `dist/frontend/browser`
6. Add environment variable:
   ```
   NG_APP_API_BASE_URL=https://jobtrackr-gateway.onrender.com
   ```
7. Deploy — Vercel assigns `jobtrackr.vercel.app` automatically

In `environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  apiBaseUrl: process.env['NG_APP_API_BASE_URL'] || ''
};
```

---

### Step 5 — Set Up CI/CD with GitHub Actions

Create `.github/workflows/deploy.yml`:

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [api-gateway, user-service, application-service, analytics-service]
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build ${{ matrix.service }}
        run: |
          cd ${{ matrix.service }}
          mvn clean package -DskipTests

      - name: Login to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./${{ matrix.service }}
          push: true
          tags: ${{ secrets.DOCKERHUB_USERNAME }}/jobtrackr-${{ matrix.service }}:latest

  deploy-render:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Trigger Render deploys
        run: |
          curl -X POST ${{ secrets.RENDER_GATEWAY_HOOK }}
          curl -X POST ${{ secrets.RENDER_USER_HOOK }}
          curl -X POST ${{ secrets.RENDER_APP_HOOK }}
          curl -X POST ${{ secrets.RENDER_ANALYTICS_HOOK }}
```

**GitHub Secrets to add:**
```
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
RENDER_GATEWAY_HOOK      ← Deploy hook URL from Render dashboard
RENDER_USER_HOOK
RENDER_APP_HOOK
RENDER_ANALYTICS_HOOK
```

Vercel auto-deploys on push with no extra config needed.

---

## Environment Variables Reference

### API Gateway
```env
USER_SERVICE_URL=https://jobtrackr-user.onrender.com
APPLICATION_SERVICE_URL=https://jobtrackr-app.onrender.com
ANALYTICS_SERVICE_URL=https://jobtrackr-analytics.onrender.com
JWT_SECRET=your-256-bit-secret
REDIS_URL=rediss://default:password@upstash-host:6379
ALLOWED_ORIGINS=https://jobtrackr.vercel.app
```

### User Service
```env
POSTGRES_URL=postgresql://user:pass@neon-host/jobtrackr?sslmode=require
JWT_SECRET=your-256-bit-secret          # Must match Gateway
JWT_EXPIRY_MS=900000                    # 15 minutes
REFRESH_TOKEN_EXPIRY_DAYS=7
REDIS_URL=rediss://default:password@upstash-host:6379
GOOGLE_CLIENT_ID=...                    # OAuth2
GOOGLE_CLIENT_SECRET=...
```

### Application Service
```env
POSTGRES_URL=postgresql://user:pass@neon-host/jobtrackr?sslmode=require
KAFKA_BOOTSTRAP_SERVERS=pkc-xxx.upstash.io:9092
KAFKA_SASL_USERNAME=...
KAFKA_SASL_PASSWORD=...
```

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
  application.yml          ← shared config
  application-local.yml    ← local overrides (Docker URLs)
  application-prod.yml     ← prod overrides (managed service URLs)
```

Run locally with: `--spring.profiles.active=local`
Render sets: `SPRING_PROFILES_ACTIVE=prod`

---

## Verifying the Deployment

After all services are up, verify end-to-end:

```bash
# 1. Health check gateway
curl https://jobtrackr-gateway.onrender.com/actuator/health

# 2. Register a user
curl -X POST https://jobtrackr-gateway.onrender.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"test@example.com","password":"Test@1234"}'

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

Since Render free services cold-start in ~30s, do this before a demo or sharing the link:

1. Hit the health check endpoint to wake all services up
2. Have a pre-seeded demo account ready with 20–30 applications across different statuses so the analytics charts look good
3. Record a **Loom video** (2–3 min) as a backup — more reliable than a live demo with a cold-start risk
4. Pin the Loom link in your GitHub README alongside the live URL
