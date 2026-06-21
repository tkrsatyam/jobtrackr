# 📋 JobTrackr — Feature List

---

## 👤 User Service Features

### Authentication & Authorization
- [x] Register with email + password
- [x] Login with JWT (access token + refresh token)
- [x] JWT refresh token rotation (per-session — logout only affects current session)
- [x] Logout (access token blacklisted in Redis, refresh token revoked in PostgreSQL)
- [ ] OAuth2 login via Google *(Phase 5 — commented out, pending Google credentials)*
- [ ] Password reset via email link *(not yet implemented)*

### Profile
- [x] View profile — id, email, fullName, avatarUrl, role, provider
- [x] Edit profile — fullName and avatarUrl only (email, role, provider are not editable)
- [x] Change password (LOCAL accounts only — Google OAuth2 users have no password)
- [x] Delete account (hard delete — removes refresh tokens first, then user record)
- [ ] Set job search preferences — target role, salary range, preferred locations, work mode *(not yet implemented)*
- [ ] Upload profile avatar via file upload *(avatarUrl is currently a plain string URL; file upload handled by Document Service in Phase 2)*

---

## 📁 Application Service Features

### Core Application CRUD
- [x] Create application — companyName (required), role (required), and all optional fields
- [x] Get application by ID (ownership verified)
- [x] Update application fields — partial update, status not updatable here
- [x] Soft delete — sets `is_deleted = true`
- [x] Archive / unarchive toggle
- [x] Get all applications with filtering and pagination
- [x] Invalid enum values return 400 with accepted values listed

### Filtering (GET /api/applications)
- [x] Filter by status, priority, workMode, isArchived
- [x] Partial case-insensitive keyword match on company and role
- [x] Date range (appliedAfter, appliedBefore)
- [x] Pagination with sortBy and sortDir
- [ ] Tag-based filtering — not yet implemented
- [ ] Full-text search — not yet implemented

### Status Pipeline
- [x] Full pipeline with validated transitions enforced by StatusTransitionValidator
- [x] Status change with optional note
- [x] Status history tracked via cascade on every change
- [x] Terminal states enforced — no further transitions
- [x] Invalid transitions return 400 with descriptive message

### Tags
- [x] Add tag (stored lowercase, duplicates silently ignored)
- [x] Remove tag by value
- [ ] Tag-based filtering — not yet implemented
- [ ] Bulk tag assignment — not yet implemented

### Bulk Actions
- [x] Bulk delete
- [x] Bulk archive (sets isArchived = true)
- [x] Bulk status change (invalid transitions silently skipped per item)
- [ ] Bulk tag assignment — not yet implemented

### Application Detail View
- [ ] Full detail page per application
- [ ] Linked documents (resumes, cover letters)
- [ ] Linked contacts (recruiter, hiring manager)
- [ ] Linked reminders
- [ ] Activity timeline (all events in one view)
- [ ] Edit all fields inline

### Document Integration
- [x] DocumentServiceClient (Feign) with fallback returning empty list
- [ ] Document Service not yet built — documents always empty (Phase 2)

### Infrastructure
- [x] /ping endpoint for Render keep-warm
- [x] Kafka producer stubbed — events logged, send calls commented out
- [x] KafkaProducerConfig excluded in prod via @Profile("!prod")
- [x] Prod profile — Neon DB, Upstash Redis, Eureka disabled, SQL logging off

---

## 🔔 Reminder Service Features

- [ ] Create a reminder per application — title, description, due date/time
- [ ] Reminder types: Follow-up, Interview, Task, Custom
- [ ] Auto-suggest: "No update in 7 days — want to follow up?"
- [ ] Recurring reminders (daily, weekly)
- [ ] Mark reminder as done
- [ ] Snooze a reminder (push by 1 day, 3 days, 1 week)
- [ ] Calendar view of all upcoming reminders
- [ ] Overdue reminders highlighted in red
- [ ] Reminders widget on dashboard

---

## 📄 Document Service Features

- [ ] Upload resume (PDF/DOCX, max 5MB)
- [ ] Upload cover letter per application
- [ ] Document versioning — Resume v1, v2, v3 with upload timestamps
- [ ] Set a "default resume" used for new applications
- [ ] Link a specific resume version to a specific application
- [ ] In-browser PDF preview
- [ ] Download document
- [ ] Delete document version
- [ ] Document metadata — upload date, file size, format, linked applications count

---

## 👥 Contact Service Features

- [ ] Add a contact to an application — name, role (Recruiter / Hiring Manager / Interviewer / Referral)
- [ ] Contact fields: email, phone, LinkedIn URL, company
- [ ] Log an interaction per contact — type (Call / Email / Message / Meeting), date, notes
- [ ] Interaction history timeline per contact
- [ ] Same contact can be linked to multiple applications
- [ ] Search contacts by name or company

---

## 🔕 Notification Service Features

- [ ] In-app notification bell with unread count badge
- [ ] Notification types:
  - Reminder due
  - Application status changed
  - Weekly summary email ("You applied to 5 jobs this week")
  - Document expiry warning (if resume hasn't been updated in 60 days)
- [ ] Mark notification as read / mark all as read
- [ ] Notification preferences — opt in/out per notification type
- [ ] Email delivery via SendGrid / JavaMailSender
- [ ] Real-time in-app delivery via WebSocket / SSE

---

## 📊 Analytics Service Features

### Dashboard Summary Cards
- [ ] Total active applications
- [ ] Applications this week / this month
- [ ] Current pipeline breakdown (how many in each status)
- [ ] Response rate (applied → any response)
- [ ] Offer rate (applied → offer)

### Charts & Graphs
- [ ] Applications over time — line chart (daily/weekly/monthly)
- [ ] Pipeline funnel chart — Applied → Screened → Interview → Offer
- [ ] Breakdown by source (LinkedIn vs Naukri vs Referral etc.)
- [ ] Breakdown by work mode (Remote vs Hybrid vs On-site)
- [ ] Breakdown by location
- [ ] Average days to response per stage
- [ ] Activity heatmap (GitHub-style — applications per day)
- [ ] Top companies by application count

### Insights
- [ ] "Your best source is LinkedIn — 60% of interviews came from there"
- [ ] "Average response time: 5 days"
- [ ] "You haven't applied anywhere in 3 days"

---

## 🖥️ Frontend (Angular) Features

### Shell & Navigation
- [x] Auth guard — redirects unauthenticated users to `/login`
- [x] Auth interceptor — attaches Bearer token to every request, handles 401 with automatic token refresh and retry
- [x] Token storage — `localStorage` with signals for reactive auth state
- [x] Shell layout with persistent sidebar and topbar
- [x] Lazy-loaded routes for all feature modules
- [x] Redirect unknown routes to dashboard
- [ ] Dark mode toggle — not yet implemented
- [ ] Global search bar — not yet implemented

### Auth Pages
- [x] Login page (`/login`)
- [x] Register page (`/register`)
- [ ] Forgot password page — not yet implemented

### Dashboard (`/dashboard`)
- [x] Stat cards — Total, Active, Offers, Accepted counts
- [x] Recent applications list (last 5 by updatedAt)
- [x] Status badges on recent list
- [ ] Upcoming reminders widget — Phase 2 (Reminder Service)
- [ ] Weekly activity chart — Phase 4 (Analytics Service)
- [ ] Quick-add application button — not yet implemented

### Applications Module

**List View (`/applications`)**
- [x] Paginated table with Material table
- [x] Filter bar — status, priority, workMode, company (text), role (text), appliedAfter (date), appliedBefore (date), isArchived (toggle)
- [x] Filters apply immediately on change, reset button clears all
- [x] Columns — company, role, status badge, priority badge, applied date, tags, actions
- [x] Row actions — view detail, archive toggle, delete with confirmation dialog
- [x] Bulk selection with select-all checkbox
- [x] Bulk action toolbar — bulk delete (with confirmation), bulk archive, bulk status change
- [x] Snackbar feedback on all operations
- [ ] Sort by column header — not yet implemented (fixed to createdAt desc)

**Kanban Board (`/applications/board`)**
- [x] Columns for all active statuses — SAVED, APPLIED, PHONE_SCREEN, INTERVIEW, TECHNICAL_ROUND, HR_ROUND, OFFER
- [x] Drag-and-drop between columns using Angular CDK
- [x] Transition validation on drop — invalid drops show snackbar error and are rejected
- [x] Valid columns highlighted, invalid columns dimmed during drag
- [x] Optimistic UI update on drop — column updates immediately, API called in background, reverts on error
- [x] Archive and delete actions per card
- [x] Terminal status cards (ACCEPTED, REJECTED, GHOSTED, WITHDRAWN) not shown as columns — handled in list view

**Create Application (`/applications/new`)**
- [x] Form with all fields — companyName, role, jobUrl, status, priority, workMode, location, salaryMin, salaryMax, currency, appliedDate, source, notes, tags
- [x] companyName and role required with inline validation
- [x] Tag input component — add on enter, remove on click

**Application Detail (`/applications/:id`)**
- [x] Full detail view — all fields displayed
- [x] Status change panel — shows allowed next statuses only, optional note input
- [x] Status history timeline — ordered newest first
- [x] Tag management inline — add new tag, remove existing tags
- [x] Archive toggle
- [x] Delete with confirmation dialog
- [x] Salary formatted with `SalaryFormatPipe` (₹ symbol, en-IN locale)
- [ ] Linked documents section — Phase 2
- [ ] Linked contacts section — Phase 2
- [ ] Linked reminders section — Phase 2

**Edit Application (`/applications/:id/edit`)**
- [x] Edit form pre-populated with existing values

### Settings (`/settings`)
- [x] View current profile (fullName, email, avatarUrl, provider, role)
- [x] Edit profile — fullName and avatarUrl
- [x] Change password (shown only for LOCAL provider users)
- [x] Delete account with confirmation dialog
- [ ] Notification preferences — Phase 3
- [ ] Connected accounts (Google OAuth) — Phase 5

### Shared Components
- [x] `StatusBadgeComponent` — color-coded chip per status
- [x] `PriorityBadgeComponent` — color-coded badge per priority level
- [x] `TagChipComponent` — displays tag in title case, removable in edit context
- [x] `StatusTimelineComponent` — vertical timeline of status history
- [x] `ConfirmDialogComponent` — reusable confirmation modal with destructive styling
- [x] `ApplicationCardComponent` — card used in kanban board
- [x] `ApplicationFormComponent` — shared form used by create and edit views
- [x] `BulkActionToolbarComponent` — appears when items selected, shows bulk action buttons
- [x] `StatusChangePanelComponent` — status change UI with allowed transitions and note input

### Pipes
- [x] `TitleCaseTagPipe` — converts `spring boot` → `Spring Boot` for tag display
- [x] `SalaryFormatPipe` — formats salary with currency symbol and locale formatting

### Constants
- [x] `STATUS_TRANSITIONS` — frontend mirror of backend transition map, used to guard UI
- [x] `TERMINAL_STATUSES` — list of terminal states, used to disable status change UI
- [x] `isTerminal()` / `getAllowedTransitions()` — utility functions
- [x] `STATUS_LABELS`, `PRIORITY_LABELS`, `WORK_MODE_LABELS`, `SOURCE_LABELS` — display label maps
- [x] `ALL_STATUSES`, `ACTIVE_STATUSES`, `ALL_PRIORITIES`, `ALL_WORK_MODES`, `ALL_SOURCES` — enum arrays for dropdowns

### Environment Config
- [x] `environment.ts` — `apiUrl: http://localhost:8080` (local dev via Gateway)
- [x] `environment.prod.ts` — `apiUrl: https://jobtrackr-gateway.onrender.com` (production)