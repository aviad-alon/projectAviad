<h1 align="center">IssueFlow</h1>
<p align="center">Ticket Management Backend Platform - AT&T TDP 2026</p>
<div align="center">
<table><tr><td align="center">
<h3>by <strong>Aviad Alon</strong> &nbsp;|&nbsp; <a href="https://aviad-alon.vercel.app" target="_blank">Get to know me more</a></h3>
</td></tr></table>
</div>

---

## Overview

IssueFlow is a backend service for a lightweight project and issue tracking platform. It manages users, projects, tickets, comments, dependencies, attachments, audit logs, and bulk CSV import/export - with auto-assignment, auto-escalation, soft delete, and JWT-based auth.

Built with Java 21 and Spring Boot. 69 unit tests, no Spring context.

> **Setup & run instructions:** see [run.md](run.md)

---

## Functionality

- **Users API** - manages user identities behind ticket assignments and comments.
- **Projects API** - manages top-level containers that group related tickets.
- **Tickets API** - manages the core work items (issues) tracked in the system.
- **Comments API** - manages user comments on tickets.
- **Audit Log API** - read-only log of all state-changing actions in the system.
- **Dependencies API** - manages ticket-to-ticket blocker relationships.
- **Attachments API** - manages file attachments on tickets.
- **Export/Import API** - supports bulk ticket export and import via CSV.
- **Soft Delete API** - tickets and projects are soft-deleted and can be restored by ADMIN users.
- **Mentions API** - `@username` mentions in comments are validated, persisted, and retrievable per user.
- **Auto-Escalation** - a background scheduler automatically escalates ticket priority when a due date is exceeded.
- **Auto-Assignment** - tickets without an explicit assignee are automatically assigned to the least-loaded DEVELOPER in the project.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.2 |
| Persistence | Spring Data JPA / Hibernate 6, PostgreSQL |
| Security | Spring Security + JJWT 0.12.6 (stateless JWT) |
| Validation | Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Size`) |
| CSV | Apache Commons CSV 1.10.0 |
| Testing | JUnit 5, Mockito (69 unit tests, zero Spring context) |
| Build | Maven Wrapper (`./mvnw`) |
| Database (local) | Docker Compose (`compose.yml`) |

---

## Key Design Decisions

- **Soft delete via `deletedAt` timestamp** - nothing is permanently deleted; records get a timestamp instead, and admins can restore them at any time.
- **Cascaded soft delete** - deleting a project also soft-deletes all its tickets in the same transaction, and restoring it brings them back too.
- **`TicketDependency` as its own entity** - I modeled dependencies as a separate table rather than a simple field, which made it easier to validate them, log them, and run cycle detection on them.
- **BFS cycle detection** - before saving a new dependency, the code runs a breadth-first search through the existing graph to make sure no cycle would be created.
- **Forward-only status transitions** - tickets can only move forward (`TODO -> IN_PROGRESS -> IN_REVIEW -> DONE`). Going backward is blocked, and closing a ticket is blocked if it still has unresolved blockers.
- **Optimistic locking (`@Version`)** - every entity has a version field. If two requests try to update the same record at the same time, one gets a `409 Conflict` instead of silently overwriting the other.
- **Centralized error handling (`GlobalExceptionHandler`)** - all exceptions go through one place and return a consistent JSON shape with `timestamp`, `status`, `error`, and `message`.
- **Token blacklist** - logging out actually invalidates the JWT on the server side, so a stolen token can't be reused.
- **Auto-assignment** - if a ticket is created without an assignee, it's automatically assigned to the developer with the fewest open tickets in that project.
- **Auto-escalation** - a background job runs every 60 seconds and bumps the priority of overdue tickets one level up (`LOW` -> `MEDIUM` -> `HIGH` -> `CRITICAL`).

---

## APIs

> All endpoints are prefixed with `/api`. All endpoints except `POST /api/users` and `POST /api/auth/login` require a valid JWT in the `Authorization: Bearer <token>` header.

### Authentication

| Description | Method | Endpoint | Request Body | Response |
|-------------|--------|----------|-------------|---------|
| Login - obtain JWT | POST | `/auth/login` | `{ "username": "jdoe", "password": "secret" }` | `{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": 3600 }` (`200`) |
| Logout - invalidate token | POST | `/auth/logout` | - | `200 OK` |
| Get current user | GET | `/auth/me` | - | User object (`200`) |

---

### Users

| Description | Method | Endpoint | Request Body | Response |
|-------------|--------|----------|-------------|---------|
| Get all users | GET | `/users` | - | List of user objects (`200`) |
| Get user by ID | GET | `/users/:userId` | - | User object (`200`) |
| Create a user | POST | `/users` | `{ "username", "email", "fullName", "password", "role" }` | Created user (`201`) |
| Update a user | PATCH | `/users/:userId` | `{ "fullName", "role" }` | Updated user object (`200`) |
| Delete a user | DELETE | `/users/:userId` | - | `204 No Content` |
| Get mentions for a user | GET | `/users/:userId/mentions?page=0&pageSize=10` | - | Paginated list of comments (`200`) |

---

### Projects

| Description | Method | Endpoint | Request Body | Response |
|-------------|--------|----------|-------------|---------|
| Get all projects | GET | `/projects` | - | List of project objects (`200`) |
| Get project by ID | GET | `/projects/:projectId` | - | Project object (`200`) |
| Create a project | POST | `/projects` | `{ "name", "description", "ownerId" }` | Created project (`201`) |
| Update a project | PATCH | `/projects/:projectId` | `{ "name", "description" }` | Updated project object (`200`) |
| Soft-delete a project | DELETE | `/projects/:projectId` | - | `204 No Content` |
| Get workload report | GET | `/projects/:projectId/workload` | - | Workload summary object (`200`) |
| List deleted projects | GET | `/projects/deleted` | - | List of deleted project objects (`200`) |
| Restore a project | POST | `/projects/:projectId/restore` | - | Restored project object (`200`) |

---

### Tickets

| Description | Method | Endpoint | Request Body | Response |
|-------------|--------|----------|-------------|---------|
| Get tickets by project | GET | `/tickets?projectId=:id` | - | List of ticket objects (`200`) |
| Get ticket by ID | GET | `/tickets/:ticketId` | - | Ticket object (`200`) |
| Create a ticket | POST | `/tickets` | `{ "title", "description", "status", "priority", "type", "projectId", "assigneeId"?, "dueDate"? }` | Created ticket (`201`) |
| Update a ticket | PATCH | `/tickets/:ticketId` | Any subset of create fields | Updated ticket object (`200`) |
| Soft-delete a ticket | DELETE | `/tickets/:ticketId` | - | `204 No Content` |
| Export tickets to CSV | GET | `/tickets/export?projectId=:id` | - | CSV file download (`200`) |
| Import tickets from CSV | POST | `/tickets/import` | `multipart/form-data: file, projectId` | Import summary object (`200`) |
| List deleted tickets | GET | `/tickets/deleted?projectId=:id` | - | List of deleted ticket objects (`200`) |
| Restore a ticket | POST | `/tickets/:ticketId/restore` | - | Restored ticket object (`200`) |

**Ticket enums:**
- `status`: `TODO` | `IN_PROGRESS` | `IN_REVIEW` | `DONE`
- `priority`: `LOW` | `MEDIUM` | `HIGH` | `CRITICAL`
- `type`: `BUG` | `FEATURE` | `TECHNICAL`

---

### Comments

| Description | Method | Endpoint | Request Body | Response |
|-------------|--------|----------|-------------|---------|
| Get comments for ticket | GET | `/tickets/:ticketId/comments` | - | List of comment objects (`200`) |
| Add a comment | POST | `/tickets/:ticketId/comments` | `{ "content": "Hello @jdoe!" }` | Created comment (`201`) |
| Update a comment | PATCH | `/tickets/:ticketId/comments/:commentId` | `{ "content": "..." }` | Updated comment object (`200`) |
| Delete a comment | DELETE | `/tickets/:ticketId/comments/:commentId` | - | `204 No Content` |

`@username` mentions in comment content are automatically resolved to user records and returned in `mentionedUsers`.

---

### Ticket Dependencies

| Description | Method | Endpoint | Request Body | Response |
|-------------|--------|----------|-------------|---------|
| Add a dependency | POST | `/tickets/:ticketId/dependencies` | `{ "blockedBy": 42 }` | Created dependency (`201`) |
| List dependencies | GET | `/tickets/:ticketId/dependencies` | - | List of dependency objects (`200`) |
| Remove a dependency | DELETE | `/tickets/:ticketId/dependencies/:blockerId` | - | `204 No Content` |

Rules: both tickets must belong to the same project; self-dependencies and duplicates are rejected.

---

### Attachments

| Description | Method | Endpoint | Request Body | Response |
|-------------|--------|----------|-------------|---------|
| Upload attachment | POST | `/tickets/:ticketId/attachments` | `multipart/form-data: file` | Created attachment (`201`) |
| Delete attachment | DELETE | `/tickets/:ticketId/attachments/:attachmentId` | - | `204 No Content` |

Allowed types: `image/png`, `image/jpeg`, `application/pdf`, `text/plain`. Max size: 10 MB.

---

### Audit Logs

| Description | Method | Endpoint | Query Params | Response |
|-------------|--------|----------|-------------|---------|
| Get audit logs | GET | `/audit-logs` | `entityType`, `entityId`, `action` (all optional) | List of audit log objects (`200`) |

Logged actions: `CREATE`, `UPDATE`, `DELETE`, `RESTORE`, `ADD_COMMENT`, `AUTO_ESCALATE`, `UPLOAD_ATTACHMENT`, `AUTO_ASSIGN`.

---

## Error Responses

All errors return a consistent JSON envelope:

```json
{
  "timestamp": "2026-05-21T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Ticket not found: 99",
  "path": "/api/tickets/99",
  "details": []
}
```

| Field | Description |
|-------|-------------|
| `timestamp` | Date and time the error occurred |
| `status` | HTTP status code (e.g. `404`) |
| `error` | Short status label (e.g. `"Not Found"`) |
| `message` | Human-readable explanation of what went wrong |
| `path` | The request URI that triggered the error |
| `details` | List of field-level validation errors (non-empty only on `400` validation failures) |

| Status | Condition |
|--------|-----------|
| `400` | Validation failure, illegal state, bad business rule |
| `401` | Missing or invalid JWT |
| `403` | Insufficient role |
| `404` | Resource not found |
| `409` | Duplicate resource, optimistic locking conflict |
| `413` | File upload exceeds 10 MB |
| `500` | Unexpected server error |

---

## Testing

The project includes 69 unit tests covering all core services. Tests run against an in-memory H2 database - no Docker required.

```bash
./mvnw test
```

Test classes:
- `TicketServiceTest` - create, update, soft-delete, restore, auto-assignment
- `ProjectServiceTest` - CRUD, soft-delete, restore, cascade to tickets
- `DependencyServiceTest` - add, remove, validation rules, cycle detection
- `CommentServiceTest` - mentions, CRUD
- `EscalationServiceTest` - priority escalation, overdue flag
- `UserServiceTest` - CRUD

---

## AI & Agents

See [prompts.md](prompts.md) for the full prompt log and working methodology.

- **Claude Code (claude-sonnet-4-6)** - implementation, testing, architecture
- **Google Gemini 2.5 Pro & Flash** - research, conceptual questions, prompt refinement

