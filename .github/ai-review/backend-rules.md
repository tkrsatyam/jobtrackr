# Backend Review Guidelines (JobTrackr Microservices)

Review any changes in `services/` against these architectural and code quality rules:

## Technology Stack & Architecture
- **Language & Runtime**: Java 21 and Spring Boot 4.x.
- **Architectural Style**: Monorepo with microservices + Spring Cloud 2025 Gateway MVC.
- **Services Architecture**:
  - `api-gateway` (`8080`): Spring Cloud Gateway MVC using programmatic `RouterFunction` bean routing (property routing is unsupported in Spring Cloud 2025.x).
  - `user-service` (`8081`): Authentication, user profiles, refresh token rotation.
  - `application-service` (`8082`): Job applications, status transition engine, status history, notes.
  - `reminder-service` (`8083`): Scheduled follow-ups and deadline reminders.
  - `document-service` (`8084`): Resumes, cover letters, and Cloudflare R2 / MinIO storage.
  - `contact-service` (`8085`): Recruiters, interviewers, and professional contacts.
  - `notification-service` (`8086`): Event-driven email and in-app alerts.
  - `analytics-service` (`8087`): Aggregated application metrics and conversion funnel analytics.
  - `email-ingestion-service`: Automated email parsing and job application synchronization.
  - `eureka-server` (`8761`): Local dev service discovery only (excluded in production profile).
- **Authentication & Security Pipeline**:
  - Short-lived JWT access tokens (15 min, Redis-blacklisted on logout) + 7-day refresh tokens rotated in PostgreSQL.
  - API Gateway validates JWT tokens, verifies the Redis blacklist, and injects downstream headers: `X-User-Id`, `X-User-Email`, `X-User-Role`.
  - Downstream services trust these gateway headers with `anyRequest().permitAll()`. Downstream services must exclude `UserDetailsServiceAutoConfiguration`.

## Persistence & Query Performance
- **Databases**: PostgreSQL (Neon) and MongoDB (Atlas).
- **Query Performance & Anti-N+1**:
  - Strictly eliminate N+1 queries using `@EntityGraph` and `@BatchSize` for entity relationships.
  - Use dynamic Specification-based filtering for multi-attribute search and filtering.
  - Flag unindexed queries, accidental full-table/collection scans, or unbounded pagination.
- **Entity Conventions**:
  - Primary keys use Hibernate `@UuidGenerator`.
  - Auditing: use `@CreationTimestamp` and `@UpdateTimestamp`.
  - JSON serialization: strictly use Jackson 3.x imports (`tools.jackson.*`).

## Pipeline Status Truth & State Machine
- Job application status transitions must strictly adhere to the transition engine:
  `SAVED → APPLIED → PHONE_SCREEN → INTERVIEW → TECHNICAL_ROUND → HR_ROUND → OFFER → ACCEPTED / REJECTED / GHOSTED / WITHDRAWN`.
- Validate that all status updates route through the backend `EnumMap` validator and persist cascade-based `StatusHistory` records.
- Enforce terminal states (`ACCEPTED`, `REJECTED`, `GHOSTED`, `WITHDRAWN`). No further transitions are permitted from terminal states.

## API & Error Handling
- REST APIs must adhere to standard HTTP status codes (`200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`).
- Global error envelope: standardized RFC 9457 `ProblemDetail` across all microservices via `ResponseEntityExceptionHandler`.
- Strictly flag empty `catch` blocks or swallowed exceptions.

## System Invariants & Framework Guarantees (Do Not Flag)
- **Gateway Trust Invariant**: Downstream microservices intentionally configure `anyRequest().permitAll()` because authentication and token verification occur at the API Gateway level. **Do not flag downstream services for missing Spring Security filter chains or missing `@PreAuthorize` on internal endpoints**.
- **Error Handling Invariant**: `ResponseEntityExceptionHandler` intercepts unhandled domain exceptions and produces RFC 9457 `ProblemDetail` envelopes. Controllers and services do not need redundant defensive `try-catch` blocks around standard business operations.
- **Header Trust Invariant**: Controller methods extract user identity directly from `@RequestHeader("X-User-Id")`. Do not flag missing `SecurityContext` lookups in downstream services.

## Security & Resilience
- Never hardcode credentials, API keys, or secrets.
- Verify ownership authorization on every state-mutating endpoint. Users must only query or mutate their own records matching `X-User-Id`.
- Ensure input validation annotations (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`) are applied to incoming request DTOs.
- Avoid resource leaks. Ensure streams, connections, and external resources are safely managed (try-with-resources).
