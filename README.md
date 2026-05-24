<h1 align="center">IssueFlow</h1>
<p align="center">Ticket Management Backend Platform - AT&T TDP 2026</p>
<div align="center">
<table><tr><td align="center">
<h3>by <strong>Aviad Alon</strong> &nbsp;|&nbsp; <a href="https://aviad-alon.vercel.app" target="_blank">Get to know me more</a></h3>
</td></tr></table>
</div>

---

## Overview

IssueFlow is a ticket management REST API built with Java 21 and Spring Boot. It handles the usual CRUD operations, but also includes things like circular dependency detection between tickets, soft-delete with full restore, status transition rules, auto-assignment of tickets to the least busy developer, and a background job that escalates overdue tickets automatically. The project has 65 unit tests and no Spring context loaded in tests.

> **Setup & run instructions:** see [run.md](run.md)

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
| Testing | JUnit 5, Mockito (65 unit tests, zero Spring context) |
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
| Login - obtain JWT | POST | `/auth/login` | `{ "username": "jdoe", "password": "secret" }` | `{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": 3600 }` |
| Logout - invalidate token | POST | `/auth/logout` | - | `200 OK` |
| Get current user | GET | `/auth/me` | - | User object |

---

### Users

| Description | Method | Endpoint | Request Body |
|-------------|--------|----------|-------------|
| Get all users | GET | `/users` | - |
| Get user by ID | GET | `/users/:userId` | - |
| Create a user | POST | `/users` | `{ "username", "email", "fullName", "password", "role" }` |
| Update a user | PATCH | `/users/:userId` | `{ "fullName", "role" }` |
| Delete a user | DELETE | `/users/:userId` | - |
| Get mentions for a user | GET | `/users/:userId/mentions?page=0&pageSize=10` | - |

---

### Projects

| Description | Method | Endpoint | Request Body |
|-------------|--------|----------|-------------|
| Get all projects | GET | `/projects` | - |
| Get project by ID | GET | `/projects/:projectId` | - |
| Create a project | POST | `/projects` | `{ "name", "description", "ownerId" }` |
| Update a project | PATCH | `/projects/:projectId` | `{ "name", "description" }` |
| Soft-delete a project | DELETE | `/projects/:projectId` | - |
| Get workload report | GET | `/projects/:projectId/workload` | - |
| List deleted projects | GET | `/projects/deleted` | - |
| Restore a project | POST | `/projects/:projectId/restore` | - |

---

### Tickets

| Description | Method | Endpoint | Request Body |
|-------------|--------|----------|-------------|
| Get tickets by project | GET | `/tickets?projectId=:id` | - |
| Get ticket by ID | GET | `/tickets/:ticketId` | - |
| Create a ticket | POST | `/tickets` | `{ "title", "description", "status", "priority", "type", "projectId", "assigneeId"?, "dueDate"? }` |
| Update a ticket | PATCH | `/tickets/:ticketId` | Any subset of create fields |
| Soft-delete a ticket | DELETE | `/tickets/:ticketId` | - |
| Export tickets to CSV | GET | `/tickets/export?projectId=:id` | - |
| Import tickets from CSV | POST | `/tickets/import` | `multipart/form-data: file, projectId` |
| List deleted tickets | GET | `/tickets/deleted?projectId=:id` | - |
| Restore a ticket | POST | `/tickets/:ticketId/restore` | - |

**Ticket enums:**
- `status`: `TODO` | `IN_PROGRESS` | `IN_REVIEW` | `DONE`
- `priority`: `LOW` | `MEDIUM` | `HIGH` | `CRITICAL`
- `type`: `BUG` | `FEATURE` | `TECHNICAL`

---

### Comments

| Description | Method | Endpoint | Request Body |
|-------------|--------|----------|-------------|
| Get comments for ticket | GET | `/tickets/:ticketId/comments` | - |
| Add a comment | POST | `/tickets/:ticketId/comments` | `{ "content": "Hello @jdoe!" }` |
| Update a comment | PATCH | `/tickets/:ticketId/comments/:commentId` | `{ "content": "..." }` |
| Delete a comment | DELETE | `/tickets/:ticketId/comments/:commentId` | - |

`@username` mentions in comment content are automatically resolved to user records and returned in `mentionedUsers`.

---

### Ticket Dependencies

| Description | Method | Endpoint | Request Body |
|-------------|--------|----------|-------------|
| Add a dependency | POST | `/tickets/:ticketId/dependencies` | `{ "blockedBy": 42 }` |
| List dependencies | GET | `/tickets/:ticketId/dependencies` | - |
| Remove a dependency | DELETE | `/tickets/:ticketId/dependencies/:blockerId` | - |

Rules: both tickets must belong to the same project; self-dependencies and duplicates are rejected.

---

### Attachments

| Description | Method | Endpoint | Request Body |
|-------------|--------|----------|-------------|
| Upload attachment | POST | `/tickets/:ticketId/attachments` | `multipart/form-data: file` |
| Delete attachment | DELETE | `/tickets/:ticketId/attachments/:attachmentId` | - |

Allowed types: `image/png`, `image/jpeg`, `application/pdf`, `text/plain`. Max size: 10 MB.

---

### Audit Logs

| Description | Method | Endpoint | Query Params |
|-------------|--------|----------|-------------|
| Get audit logs | GET | `/audit-logs` | `entityType`, `entityId`, `action` (all optional) |

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
  "details": []
}
```

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

The project includes 65 unit tests covering all core services. Tests run against an in-memory H2 database - no Docker required.

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

