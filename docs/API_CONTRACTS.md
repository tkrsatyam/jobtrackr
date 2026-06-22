# 📡 JobTrackr — API Contracts

> All requests go through the API Gateway at `http://localhost:8080`.
> Protected endpoints require `Authorization: Bearer <jwt_token>` header.

---

## 🔐 Auth (User Service) — `/api/auth`

### POST `/api/auth/register`
Register a new user.

**Request:**
```json
{
  "fullName": "Rahul Sharma",
  "email": "rahul@example.com",
  "password": "SecurePass@123"
}
```
**Response `200`:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "uuid-string",
  "tokenType": "Bearer",
  "userId": "uuid",
  "email": "rahul@example.com",
  "fullName": "Rahul Sharma",
  "role": "USER"
}
```

---

### POST `/api/auth/login`
Login and receive tokens.

**Request:**
```json
{
  "email": "rahul@example.com",
  "password": "SecurePass@123"
}
```
**Response `200`:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "uuid-string",
  "tokenType": "Bearer",
  "userId": "uuid",
  "email": "rahul@example.com",
  "fullName": "Rahul Sharma",
  "role": "USER"
}
```

---

### POST `/api/auth/refresh`
Refresh an access token. The old refresh token is rotated — a new one is issued and the old one is revoked.

**Request:**
```json
{ "refreshToken": "uuid-string" }
```
**Response `200`:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "new-uuid-string",
  "tokenType": "Bearer",
  "userId": "uuid",
  "email": "rahul@example.com",
  "fullName": "Rahul Sharma",
  "role": "USER"
}
```

---

### POST `/api/auth/logout` 🔒
Invalidate the current session. Blacklists the access token in Redis and revokes the current session's refresh token. Other active sessions on other devices are unaffected.

**Request:**
```json
{ "refreshToken": "uuid-string" }
```
**Response `204`:** No content.

---

## 👤 User Profile (User Service) — `/api/users`

### GET `/api/users/me` 🔒
Get current user profile.

**Response `200`:**
```json
{
  "id": "uuid",
  "email": "rahul@example.com",
  "fullName": "Rahul Sharma",
  "avatarUrl": "https://r2.../avatar.jpg",
  "role": "USER",
  "provider": "LOCAL"
}
```

> `provider` is either `LOCAL` (email/password) or `GOOGLE` (OAuth2). Use this on the frontend to conditionally show/hide the password change section.

---

### PUT `/api/users/me` 🔒
Update profile. Only `fullName` and `avatarUrl` are editable. Email, role, and provider are not updatable via this endpoint.

**Request:**
```json
{
  "fullName": "Rahul Sharma",
  "avatarUrl": "https://r2.../avatar.jpg"
}
```
**Response `200`:** Updated profile object (same shape as `GET /api/users/me`).

> Avatar file upload is handled by the Document Service. Angular uploads the file there first, receives a URL back, then sends that URL here to save it on the user profile.

---

### GET `/ping`
Health check endpoint for Render keep-warm pings. No auth required.

**Response `200`:** `"User service: pong"`

---

## 📁 Applications (Application Service) — `/api/applications`

### GET `/api/applications` 🔒
Get all applications for the current user.

**Query Params:**

| Param | Type | Description |
|---|---|---|
| `status` | string | Filter by status enum value (e.g. `APPLIED`) |
| `priority` | string | Filter by priority (`LOW`, `MEDIUM`, `HIGH`, `DREAM`) |
| `workMode` | string | Filter by work mode (`REMOTE`, `HYBRID`, `ON_SITE`) |
| `isArchived` | boolean | Filter by archived state (`true` / `false`) |
| `company` | string | Partial, case-insensitive match on company name |
| `role` | string | Partial, case-insensitive match on role |
| `appliedAfter` | date | Applied date from (ISO 8601 — `YYYY-MM-DD`) |
| `appliedBefore` | date | Applied date to (ISO 8601 — `YYYY-MM-DD`) |
| `sortBy` | string | Field to sort by (default: `createdAt`) |
| `sortDir` | string | `asc` or `desc` (default: `desc`) |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |

**Response `200`:**
```json
{
  "content": [
    {
      "applicationId": "uuid",
      "userId": "uuid",
      "companyName": "Google",
      "role": "Software Engineer II",
      "jobUrl": null,
      "status": "INTERVIEW",
      "priority": "DREAM",
      "workMode": "HYBRID",
      "location": "Bangalore",
      "salaryMin": 2000000,
      "salaryMax": 2500000,
      "currency": "INR",
      "appliedDate": "2024-01-10",
      "source": "LINKEDIN",
      "notes": null,
      "isArchived": false,
      "isDeleted": false,
      "createdAt": "2024-01-10T10:00:00",
      "updatedAt": "2024-01-14T09:00:00",
      "tags": ["product-company", "dream-job"],
      "statusHistory": []
    }
  ],
  "totalElements": 47,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

---

### POST `/api/applications` 🔒
Create a new application.

**Request:**
```json
{
  "companyName": "Zepto",
  "role": "Backend Engineer",
  "jobUrl": "https://jobs.zepto.com/backend-123",
  "status": "APPLIED",
  "priority": "HIGH",
  "workMode": "HYBRID",
  "location": "Mumbai",
  "salaryMin": 1200000,
  "salaryMax": 1800000,
  "currency": "INR",
  "appliedDate": "2024-01-15",
  "source": "NAUKRI",
  "tags": ["startup", "high-growth"],
  "notes": "Applied through Naukri. JD looks great, matched 90% of skills."
}
```
**Response `201`:** Full application object.

---

### GET `/api/applications/{id}` 🔒
Get single application with full detail.

**Response `200`:**
```json
{
  "applicationId": "uuid",
  "userId": "uuid",
  "companyName": "Zepto",
  "role": "Backend Engineer",
  "jobUrl": "https://...",
  "status": "INTERVIEW",
  "priority": "HIGH",
  "workMode": "HYBRID",
  "location": "Mumbai",
  "salaryMin": 1200000,
  "salaryMax": 1800000,
  "currency": "INR",
  "appliedDate": "2024-01-15",
  "source": "NAUKRI",
  "notes": "...",
  "isArchived": false,
  "isDeleted": false,
  "createdAt": "2024-01-15T10:00:00",
  "updatedAt": "2024-01-20T11:00:00",
  "tags": ["startup"],
  "statusHistory": [
    { "status": "APPLIED", "note": "Application created", "changedAt": "2024-01-15T10:00:00" },
    { "status": "PHONE_SCREEN", "note": "HR called", "changedAt": "2024-01-18T14:00:00" },
    { "status": "INTERVIEW", "note": "Round 1 done, went well", "changedAt": "2024-01-20T11:00:00" }
  ]
}
```

---

### PUT `/api/applications/{id}` 🔒
Update application fields.

**Request:** Any subset of application fields.

---

### DELETE `/api/applications/{id}` 🔒
Soft delete an application.

**Response `204`:** No content.

---

### PUT `/api/applications/{id}/archive` 🔒
Toggle archive state. Archives an unarchived application; unarchives an archived one.

**Response `200`:** Full `ApplicationResponse` with updated `isArchived` value.

---

### PUT `/api/applications/{id}/status` 🔒
Change application status.

**Request:**
```json
{
  "status": "INTERVIEW",
  "note": "Round 1 scheduled for Friday 2pm"
}
```
**Response `200`:** Updated application with new status history entry.

---

### GET `/api/applications/{id}/status/history` 🔒
Get full status history for an application, ordered newest first.

**Response `200`:**
```json
[
  { "status": "PHONE_SCREEN", "note": "HR called", "changedAt": "2024-01-18T14:00:00" },
  { "status": "APPLIED", "note": "Application created", "changedAt": "2024-01-15T10:00:00" }
]
```
Note: `changedAt` serializes as `LocalDateTime` — no timezone offset or `Z` suffix.

---

### POST `/api/applications/{id}/tags` 🔒
Add a tag to an application. Tags are stored lowercase. Adding a duplicate is a no-op.

**Request:**
```json
{ "tag": "Java" }
```
**Response `200`:** Full `ApplicationResponse` with updated `tags` list.

---

### DELETE `/api/applications/{id}/tags/{tag}` 🔒
Remove a tag from an application. Removing a non-existent tag is a no-op.

**Response `200`:** Full `ApplicationResponse` with updated `tags` list.

---

### POST `/api/applications/bulk/delete` 🔒
Soft delete multiple applications.

**Request:**
```json
{ "ids": ["uuid1", "uuid2", "uuid3"] }
```
**Response `204`:** No content.

---

### POST `/api/applications/bulk/archive` 🔒
Archive multiple applications (sets `isArchived = true`).

**Request:**
```json
{ "ids": ["uuid1", "uuid2", "uuid3"] }
```
**Response `204`:** No content.

---

### POST `/api/applications/bulk/status` 🔒
Change status for multiple applications. Invalid transitions are silently skipped per application.

**Request:**
```json
{
  "ids": ["uuid1", "uuid2", "uuid3"],
  "status": "PHONE_SCREEN"
}
```
**Response `204`:** No content.

---

### GET `/ping`
Health check endpoint for Render keep-warm pings. No auth required.

**Response `200`:** `"Application service: pong"`

---

## 🔔 Reminders (Reminder Service) — `/api/reminders`

### GET `/api/reminders` 🔒
Get reminders for current user.

**Query Params:** `applicationId`, `status` (`PENDING,DONE,SNOOZED`), `from`, `to`

**Response `200`:**
```json
{
  "content": [
    {
      "reminderId": "uuid",
      "applicationId": "uuid",
      "companyName": "Zepto",
      "title": "Follow up with HR",
      "type": "FOLLOW_UP",
      "dueAt": "2024-01-22T09:00:00Z",
      "status": "PENDING",
      "isOverdue": false
    }
  ]
}
```

---

### POST `/api/reminders` 🔒
Create a reminder.

**Request:**
```json
{
  "applicationId": "uuid",
  "title": "Follow up if no response",
  "description": "Send a polite follow-up email",
  "type": "FOLLOW_UP",
  "dueAt": "2024-01-22T09:00:00Z"
}
```

---

### PATCH `/api/reminders/{id}/snooze` 🔒
Snooze a reminder.

**Request:**
```json
{ "snoozeUntil": "2024-01-25T09:00:00Z" }
```

---

### PATCH `/api/reminders/{id}/done` 🔒
Mark reminder as done.

---

## 📄 Documents (Document Service) — `/api/documents`

### GET `/api/documents` 🔒
List all documents for user.

**Response `200`:**
```json
{
  "content": [
    {
      "documentId": "uuid",
      "fileName": "Rahul_Resume_v3.pdf",
      "type": "RESUME",
      "version": 3,
      "fileSize": 245000,
      "mimeType": "application/pdf",
      "isDefault": true,
      "linkedApplicationsCount": 5,
      "uploadedAt": "2024-01-10T10:00:00Z"
    }
  ]
}
```

---

### POST `/api/documents/upload` 🔒
Upload a document.

**Request:** `multipart/form-data`
- `file`: The document file
- `type`: `RESUME` or `COVER_LETTER`
- `applicationId` (optional): Link to a specific application

**Response `201`:** Document metadata object.

---

### GET `/api/documents/{id}/preview` 🔒
Get a pre-signed URL for in-browser preview.

**Response `200`:**
```json
{ "previewUrl": "https://minio.../presigned-url", "expiresAt": "2024-01-15T11:00:00Z" }
```

---

### DELETE `/api/documents/{id}` 🔒
Delete a document.

---

## 👥 Contacts (Contact Service) — `/api/contacts`

### GET `/api/contacts` 🔒
List contacts (optionally filtered by applicationId).

---

### POST `/api/contacts` 🔒
Create a contact.

**Request:**
```json
{
  "applicationId": "uuid",
  "name": "Priya Menon",
  "role": "RECRUITER",
  "email": "priya@zepto.com",
  "phone": "+91-9876543210",
  "linkedinUrl": "https://linkedin.com/in/priya"
}
```

---

### POST `/api/contacts/{id}/interactions` 🔒
Log an interaction with a contact.

**Request:**
```json
{
  "type": "EMAIL",
  "date": "2024-01-18",
  "notes": "Sent follow-up. She replied saying they're reviewing."
}
```

---

## 📊 Analytics (Analytics Service) — `/api/analytics`

### GET `/api/analytics/summary` 🔒
Dashboard summary stats.

**Response `200`:**
```json
{
  "totalApplications": 47,
  "activeApplications": 23,
  "thisWeek": 5,
  "thisMonth": 18,
  "responseRate": 0.38,
  "offerRate": 0.06,
  "pipelineBreakdown": {
    "SAVED": 4,
    "APPLIED": 12,
    "PHONE_SCREEN": 3,
    "INTERVIEW": 2,
    "OFFER": 1,
    "REJECTED": 18,
    "GHOSTED": 7
  }
}
```

---

### GET `/api/analytics/timeline` 🔒
Applications over time.

**Query Params:** `from`, `to`, `groupBy` (`DAY,WEEK,MONTH`)

**Response `200`:**
```json
{
  "data": [
    { "period": "2024-01-01", "count": 3 },
    { "period": "2024-01-08", "count": 7 }
  ]
}
```

---

### GET `/api/analytics/funnel` 🔒
Pipeline funnel data.

---

### GET `/api/analytics/breakdown` 🔒
Breakdown by dimension.

**Query Params:** `dimension` (`SOURCE,WORK_MODE,LOCATION`)

---

## 🔕 Notifications (Notification Service) — `/api/notifications`

### GET `/api/notifications` 🔒
Get notifications for current user.

**Query Params:** `read` (boolean), `page`, `size`

**Response `200`:**
```json
{
  "unreadCount": 3,
  "content": [
    {
      "notificationId": "uuid",
      "type": "REMINDER_DUE",
      "title": "Reminder: Follow up with Zepto HR",
      "message": "Your follow-up reminder is due today",
      "read": false,
      "relatedEntityId": "reminder-uuid",
      "createdAt": "2024-01-22T09:00:00Z"
    }
  ]
}
```

---

### PATCH `/api/notifications/{id}/read` 🔒
Mark a notification as read.

---

### PATCH `/api/notifications/read-all` 🔒
Mark all as read.

---

### GET `/api/notifications/preferences` 🔒
Get notification preferences.

### PUT `/api/notifications/preferences` 🔒
Update notification preferences.

**Request:**
```json
{
  "reminderDue": true,
  "statusChanged": true,
  "weeklySummary": true,
  "documentExpiry": false,
  "emailEnabled": true,
  "inAppEnabled": true
}
```

---

## ⚠️ Error Response Format

### Application Service (`GlobalExceptionHandler`)

Standard errors:
```json
{ "error": "Descriptive error message", "timestamp": "2024-01-15T10:00:00" }
```

Validation errors (`@Valid` failures):
```json
{
  "error": "Validation failed",
  "fieldErrors": { "companyName": "Company name is required", "role": "Role is required" },
  "timestamp": "2024-01-15T10:00:00"
}
```

Invalid enum value (e.g. passing `"APPLYED"` for status):
```json
{
  "error": "Invalid value 'APPLYED' for field 'status'. Accepted values are: [SAVED, APPLIED, PHONE_SCREEN, ...]",
  "timestamp": "2024-01-15T10:00:00"
}
```

Note: `timestamp` is `LocalDateTime` — no timezone offset or `Z` suffix.

---

### User Service (`GlobalExceptionHandler`)

Runtime errors:
```json
{ "error": "Descriptive error message" }
```

Validation errors return a flat map of field names to messages:
```json
{
  "email": "must be a well-formed email address",
  "password": "Password must be at least 8 characters"
}
```
---

**Planned standardised format** (to be implemented across all services in a later phase):
```json
{
  "timestamp": "2024-01-15T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/applications",
  "details": [
    { "field": "companyName", "message": "Company name is required" }
  ]
}
```

> Other services may use a different error shape until standardisation is done.

**Common HTTP Status Codes:**
- `200` OK
- `201` Created
- `204` No Content
- `400` Bad Request — validation errors, invalid credentials, duplicate email, business rule violations
- `401` Unauthorized — missing or invalid JWT (rejected by Gateway before reaching User Service)
- `403` Forbidden — accessing another user's resource *(enforced per service once ownership checks are added)*
- `404` Not Found
- `409` Conflict — planned for duplicate resource cases (e.g. duplicate email on register); currently returns `400`
- `500` Internal Server Error