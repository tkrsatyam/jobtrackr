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
- [ ] Create a job application with fields:
  - Company name, role/title, job URL
  - Salary range (min/max + currency)
  - Location, work mode (Remote / Hybrid / On-site)
  - Application source (LinkedIn, Naukri, Referral, Company Website, etc.)
  - Date applied
  - Priority flag (Low / Medium / High / Dream Job)
  - Tags/labels (custom, e.g. "startup", "product company", "stretch role")
  - Notes (rich text)
- [ ] View all applications with list view and Kanban board view
- [ ] Search applications by company name, role, tags
- [ ] Filter by status, priority, location, work mode, date range
- [ ] Sort by date applied, company name, last updated
- [ ] Pagination and infinite scroll

### Status Pipeline
- [ ] Full pipeline: `Saved → Applied → Phone Screen → Interview → Technical Round → HR Round → Offer → Accepted / Rejected / Ghosted / Withdrawn`
- [ ] Change status with optional notes per transition
- [ ] Timeline view — every status change logged with timestamp and note
- [ ] Color-coded status badges
- [ ] Kanban drag-and-drop to change status

### Bulk Actions
- [ ] Select multiple applications
- [ ] Bulk status change
- [ ] Bulk archive
- [ ] Bulk delete (soft delete)
- [ ] Bulk tag assignment

### Application Detail View
- [ ] Full detail page per application
- [ ] Linked documents (resumes, cover letters)
- [ ] Linked contacts (recruiter, hiring manager)
- [ ] Linked reminders
- [ ] Activity timeline (all events in one view)
- [ ] Edit all fields inline

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
- [ ] Auth pages — Login, Register, Forgot Password
- [ ] Persistent sidebar navigation
- [ ] Responsive layout (desktop-first, mobile-friendly)
- [ ] Dark mode toggle
- [ ] Global search bar (search across applications and contacts)

### Dashboard
- [ ] Summary stat cards
- [ ] Recent applications list
- [ ] Upcoming reminders widget
- [ ] Quick-add application button
- [ ] Weekly activity chart

### Applications Module
- [ ] List view with filters and sorting
- [ ] Kanban board view with drag-and-drop columns
- [ ] Application detail page with tabs (Overview, Documents, Contacts, Reminders, Timeline)
- [ ] Create/edit application modal or page
- [ ] Status change with confirmation and note

### Calendar View
- [ ] Monthly calendar showing interviews and reminder due dates
- [ ] Click a date to see that day's events
- [ ] Quick-create reminder from calendar

### Analytics Module
- [ ] Full analytics page with all charts
- [ ] Date range picker filter
- [ ] Export report as PDF

### Settings
- [ ] Profile edit
- [ ] Notification preferences
- [ ] Password change
- [ ] Connected accounts (Google OAuth)
- [ ] Danger zone — delete account
