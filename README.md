<p align="center">
  <img src="https://spring.io/img/spring-2.svg" width="180" alt="Spring Boot" />
</p>

<h1 align="center">IssueFlow</h1>
<p align="center">Ticket Management Backend Platform - AT&T TDP 2026</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.2-brightgreen?logo=springboot" />
  <img src="https://img.shields.io/badge/PostgreSQL-18-blue?logo=postgresql" />
  <img src="https://img.shields.io/badge/Swagger-OpenAPI%203.0-85EA2D?logo=swagger" />
  <img src="https://img.shields.io/badge/Tests-62%20passing-success" />
</p>

---

## Overview

IssueFlow is a production-grade REST API for project and issue tracking, built with Java 21 and Spring Boot 3.4.2. The system covers the full lifecycle of tickets, projects, users, comments, dependencies, attachments, and audit logs, with JWT-based authentication throughout.

> **Interactive API docs:** start the server and open `http://localhost:8080/swagger-ui/index.html`
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
| API Docs | springdoc-openapi 2.8.3 (Swagger UI) |
| CSV | Apache Commons CSV 1.10.0 |
| Testing | JUnit 5, Mockito (62 unit tests, zero Spring context) |
| Build | Maven Wrapper (`./mvnw`) |
| Database (local) | Docker Compose (`compose.yml`) |

---

## Key Design Decisions

- **Soft delete via `deletedAt` timestamp** - records are never hard-deleted; the exact deletion time is preserved and admins can restore them at any time.
- **`TicketDependency` as a first-class entity** - dependencies have their own repository and service (`DependencyService`), enabling audit logging, cross-project validation, and duplicate detection.
- **Optimistic locking (`@Version`)** - all mutable entities carry a version column; concurrent updates return `409 Conflict` instead of silently overwriting data.
- **Centralized error handling (`GlobalExceptionHandler`)** - every exception maps to a consistent JSON envelope with `timestamp`, `status`, `error`, and `message`.
- **Token blacklist** - `POST /api/auth/logout` invalidates the JWT server-side so stolen tokens cannot be reused.
- **Auto-assignment** - tickets created without an explicit assignee are routed to the DEVELOPER with the fewest open tickets in that project.
- **Auto-escalation** - a background scheduler runs every 60 seconds and promotes overdue tickets one priority level (`LOW` -> `MEDIUM` -> `HIGH` -> `CRITICAL`).

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

The project includes 62 unit tests covering all core services. Tests run against an in-memory H2 database - no Docker required.

```bash
./mvnw test
```

Test classes:
- `TicketServiceTest` - create, update, soft-delete, restore, auto-assignment
- `ProjectServiceTest` - CRUD, soft-delete, restore
- `DependencyServiceTest` - add, remove, validation rules
- `CommentServiceTest` - mentions, CRUD
- `EscalationServiceTest` - priority escalation, overdue flag
- `UserServiceTest` - CRUD

---

## AI & Agents

This project was developed with the assistance of AI tools. See [prompts.md](prompts.md) for the full list of prompts used throughout the implementation.

**Tools used:**
- Claude Code (claude-sonnet-4-6) - implementation, testing, architecture
- Google Gemini 2.5 Pro & Flash - research and review

---

## License

This project is [MIT licensed](LICENSE).
