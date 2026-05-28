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
  "name": "Rahul Sharma",
  "email": "rahul@example.com",
  "password": "SecurePass@123"
}
```
**Response `201`:**
```json
{
  "userId": "uuid",
  "name": "Rahul Sharma",
  "email": "rahul@example.com",
  "createdAt": "2024-01-15T10:00:00Z"
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
  "refreshToken": "eyJhbGc...",
  "expiresIn": 3600,
  "user": {
    "userId": "uuid",
    "name": "Rahul Sharma",
    "email": "rahul@example.com"
  }
}
```

---

### POST `/api/auth/refresh`
Refresh an access token.

**Request:**
```json
{ "refreshToken": "eyJhbGc..." }
```
**Response `200`:**
```json
{ "accessToken": "eyJhbGc...", "expiresIn": 3600 }
```

---

### POST `/api/auth/logout` 🔒
Invalidate current token.

**Response `204`:** No content.

---

## 👤 User Profile (User Service) — `/api/users`

### GET `/api/users/me` 🔒
Get current user profile.

**Response `200`:**
```json
{
  "userId": "uuid",
  "name": "Rahul Sharma",
  "email": "rahul@example.com",
  "avatarUrl": "https://minio.../avatar.jpg",
  "preferences": {
    "targetRole": "Full Stack Developer",
    "targetSalaryMin": 800000,
    "targetSalaryMax": 1200000,
    "currency": "INR",
    "preferredLocations": ["Pune", "Bangalore", "Remote"],
    "preferredWorkMode": "HYBRID"
  },
  "createdAt": "2024-01-15T10:00:00Z"
}
```

---

### PUT `/api/users/me` 🔒
Update profile.

**Request:**
```json
{
  "name": "Rahul Sharma",
  "preferences": {
    "targetRole": "Backend Developer",
    "targetSalaryMin": 900000,
    "targetSalaryMax": 1400000,
    "preferredLocations": ["Remote"],
    "preferredWorkMode": "REMOTE"
  }
}
```
**Response `200`:** Updated user object.

---

## 📁 Applications (Application Service) — `/api/applications`

### GET `/api/applications` 🔒
Get all applications for the current user.

**Query Params:**
| Param | Type | Description |
|---|---|---|
| `status` | string | Filter by status (e.g. `APPLIED,INTERVIEW`) |
| `priority` | string | `LOW,MEDIUM,HIGH,DREAM` |
| `workMode` | string | `REMOTE,HYBRID,ON_SITE` |
| `search` | string | Search company or role name |
| `tags` | string | Comma-separated tags |
| `dateFrom` | date | Applied date from (ISO 8601) |
| `dateTo` | date | Applied date to (ISO 8601) |
| `sortBy` | string | `appliedDate,companyName,updatedAt` (default: `updatedAt`) |
| `sortDir` | string | `asc,desc` (default: `desc`) |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |

**Response `200`:**
```json
{
  "content": [
    {
      "applicationId": "uuid",
      "companyName": "Google",
      "role": "Software Engineer II",
      "status": "INTERVIEW",
      "priority": "DREAM",
      "workMode": "HYBRID",
      "location": "Bangalore",
      "salaryMin": 2000000,
      "salaryMax": 2500000,
      "currency": "INR",
      "appliedDate": "2024-01-10",
      "tags": ["product-company", "dream-job"],
      "source": "LINKEDIN",
      "lastUpdated": "2024-01-14T09:00:00Z"
    }
  ],
  "totalElements": 47,
  "totalPages": 3,
  "currentPage": 0,
  "pageSize": 20
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
  "tags": ["startup"],
  "notes": "...",
  "statusHistory": [
    { "status": "APPLIED", "changedAt": "2024-01-15T10:00:00Z", "note": "" },
    { "status": "PHONE_SCREEN", "changedAt": "2024-01-18T14:00:00Z", "note": "HR called, scheduled for 20th" },
    { "status": "INTERVIEW", "changedAt": "2024-01-20T11:00:00Z", "note": "Round 1 done, went well" }
  ],
  "linkedDocuments": ["doc-uuid-1"],
  "linkedContacts": ["contact-uuid-1"],
  "linkedReminders": ["reminder-uuid-1"],
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-20T11:00:00Z"
}
```

---

### PATCH `/api/applications/{id}` 🔒
Update application fields.

**Request:** Any subset of application fields.

---

### PATCH `/api/applications/{id}/status` 🔒
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

### DELETE `/api/applications/{id}` 🔒
Soft delete an application.

**Response `204`:** No content.

---

### POST `/api/applications/bulk` 🔒
Bulk actions on applications.

**Request:**
```json
{
  "applicationIds": ["uuid1", "uuid2"],
  "action": "ARCHIVE",
  "payload": {}
}
```
Actions: `ARCHIVE`, `DELETE`, `CHANGE_STATUS`, `ADD_TAG`

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

All errors follow this standard shape:

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

**Common HTTP Status Codes:**
- `200` OK
- `201` Created
- `204` No Content
- `400` Bad Request (validation errors)
- `401` Unauthorized (missing/invalid JWT)
- `403` Forbidden (accessing another user's resource)
- `404` Not Found
- `409` Conflict (e.g. duplicate email on register)
- `500` Internal Server Error
