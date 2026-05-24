# IssueFlow - AI Prompts Log

## Tools Used

**Claude Code (claude-sonnet-4-6)** - used as the primary coding assistant throughout the project. Responsible for all code generation, implementation, refactoring, bug fixes, and running tests directly in the terminal via the CLI.

**Google Gemini 2.5 Pro & Gemini 2.5 Flash** - used in the early and ongoing phases for understanding the assignment requirements, brainstorming the overall architecture, generating and refining prompts, answering conceptual questions (e.g., Spring Security flow, JPA relationships, JWT internals), and expanding knowledge on relevant topics before diving into implementation.

---

## Working Methodology

Claude Code was used via a standard subscription inside Visual Studio Code. The agent had direct access to the terminal and the full project directory throughout the entire development session.

The workflow followed a structured plan prepared in advance during Step 0 - before writing any code, I asked the agent to analyze the full assignment and produce a 3-day implementation plan. That plan served as the backbone for all subsequent prompts, which were written and refined based on it rather than improvised on the fly.

For each completed step, I researched the topic independently and asked the agent to produce organized documentation alongside the code - so I could understand what was generated and why, not just accept it as output. This made it easier to review each layer before moving to the next.

**Challenge - context window exhaustion:**
The main technical challenge was that the conversation context filled up quickly during long implementation sessions. My solution was to request a structured handoff summary before ending each session:

*"We are about to close this conversation and open a fresh one. Please generate a `project_status.md` file containing: a short description of the current architecture, which components and code files have been fully written (including key function signatures), important technical decisions we made, and the exact list of next steps to continue the next vertical slice."*

**Code review:**
Every piece of generated code went through a review pass - both by the agent (asked to self-review for bugs, edge cases, and consistency) and by me. Corrections and improvements were applied iteratively before moving forward.

---

## Step 0 - Project Analysis & Planning

Read the full assignment and all the project files I've attached. Before we write a single line of code I need to understand exactly what's required - which APIs need to exist, what the skeleton already provides, and what still needs to be built from scratch. Based on that, give me a 3-day implementation plan ordered by dependencies (so we never build something that relies on code that doesn't exist yet). Also list the main technical decisions I'll need to make along the way - things like how to model relationships, what security approach to use, how soft delete should work, and so on. I want the plan broken into clear daily milestones so I can track progress.

---

## Step 0.5 - First Vertical Slice

Before expanding to the full system, let's validate the setup with a single working feature. Start with user management only - create the `User` entity, `UserRepository`, a `UserService` with basic CRUD methods, and a `UserController` with at least a `POST /api/users` endpoint. Don't touch projects, tickets, or auth yet. The goal is just to confirm that the Spring Boot app starts, the database connection works, and I can create a user and get a 201 back. Keep it minimal for now.

---

## Step 1 - Database Schema

Generate the full PostgreSQL schema for IssueFlow. I need tables for: users, projects, tickets, comments, mentions (linking comments to users), ticket dependencies (one ticket blocking another), attachments, and audit logs. A few specific things to keep in mind:

- Both tickets and projects need soft delete support - use a `deleted_at` timestamp column, not a boolean flag
- Tickets need a `due_date`, a `priority` (LOW/MEDIUM/HIGH/CRITICAL), a `status` (TODO/IN_PROGRESS/IN_REVIEW/DONE), a `type` (BUG/FEATURE/TECHNICAL), and an `is_overdue` boolean
- The mentions table should be a join table between comments and users
- Dependencies should be a separate table with `ticket_id` and `blocked_by_id` columns - not just a field on the ticket
- Audit logs need entity type, entity ID, action name, and a timestamp

Make sure the schema can support all the features: auto-assignment, scheduled escalation, mentions, and dependency cycle detection.

---

## Step 2 - Entities & Enums

Now translate the schema into JPA entities and Java enums. Use Lombok on all entities (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`). A few important constraints:

- The `isOverdue` field on `Ticket` must persist to the database - map it with `@Column`, not `@Transient`
- Add `@Version` for optimistic locking on entities that could have concurrent updates (tickets, comments)
- Bidirectional relationships will cause infinite loops during JSON serialization - handle that with `@JsonManagedReference` / `@JsonBackReference` or `@JsonIgnore` where appropriate
- Enums: `TicketStatus` (TODO, IN_PROGRESS, IN_REVIEW, DONE), `TicketPriority` (LOW, MEDIUM, HIGH, CRITICAL), `TicketType` (BUG, FEATURE, TECHNICAL), `UserRole` (ADMIN, DEVELOPER)
- Use `@CreationTimestamp` and `@UpdateTimestamp` for audit fields instead of setting them manually

---

## Step 3 - DTOs

Generate all the request and response DTOs. The key rules:

- All request DTOs need Jakarta Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`, etc.)
- Passwords must never appear in any response DTO - only in the request
- `CommentResponse` should include a `List<UserSummaryResponse>` for the mentioned users
- PATCH request DTOs need to support partial updates - every field should be `Optional<String>` or nullable so that fields not included in the request body simply aren't updated. Don't update a field just because it was omitted
- Separate DTOs for create vs update where the validation rules differ (for example, `CreateTicketRequest` vs `UpdateTicketRequest`)

---

## Step 4 - Repositories

Generate all the Spring Data JPA repositories. Use Spring Data derived method names wherever the query is simple enough. For more complex queries, use `@Query` with JPQL. The specific queries I'll need:

- Count open (non-DONE, non-deleted) tickets per developer in a given project - needed for auto-assignment
- Find overdue tickets that haven't reached CRITICAL priority yet - needed for the escalation scheduler
- Find CRITICAL tickets where `isOverdue` is still false - needed to set the flag
- Paginated list of mentions by user ID (for the `GET /users/:id/mentions` endpoint) - return a `Page<Comment>`

Make sure all queries for non-deleted records filter by `deletedAt IS NULL`.

---

## Step 5 - JWT Security

Add Spring Security with stateless JWT authentication. The requirements:

- Public endpoints (no token needed): `POST /api/users` and `POST /api/auth/login`
- Everything else requires a valid JWT in the `Authorization: Bearer <token>` header
- JWT should include the user ID and role as claims, and expire after 24 hours
- Logout must actually invalidate the token on the server side - keep a blacklist in memory (a `Set<String>` is fine) so a logged-out token can't be reused even if it hasn't expired yet
- Map `UserRole.ADMIN` to Spring Security authority `ROLE_ADMIN` and `UserRole.DEVELOPER` to `ROLE_DEVELOPER`
- Return 401 for missing/invalid tokens and 403 for valid tokens with insufficient role

---

## Step 6.1 - AuditLogService

Before writing any other service, let's get the audit log in place since everything else will depend on it. Implement `AuditLogService` with a single public method: `log(String action, String entityType, Long entityId, User performedBy)`. It should set the timestamp to `LocalDateTime.now()` and save the entry to the database. The `performedBy` field should be nullable to support system-triggered actions like auto-assignment and escalation.

---

## Step 6.2 - UserService & AuthService

Now implement `UserService` and `AuthService`.

`UserService` should handle full user lifecycle: register (validate that username and email are unique, hash the password, log `CREATE`), `getAllUsers`, `getUserById`, `updateUser` (partial - only apply non-null fields, skip email uniqueness check if it didn't change), `deleteUser`, and `getUserMentions(userId, page, pageSize)` which returns a paginated list of comments where the user was mentioned.

`AuthService` needs three things: `login` - authenticate via Spring's `AuthenticationManager` and return a JWT, `logout` - add the token to an in-memory blacklist so it can't be reused, `getMe` - return the current user object given a username.

---

## Step 6.3 - ProjectService

Implement `ProjectService` to handle the full project lifecycle:

- `createProject` - validate the owner exists, save, log `CREATE`
- `getProjectById` - throw `ResourceNotFoundException` if the project doesn't exist or is soft-deleted
- `getAllActiveProjects` and `getDeletedProjects`
- `updateProject` - partial update, only apply non-null fields
- `softDeleteProject` - set `deletedAt = now()`, log `DELETE`
- `restoreProject` - clear `deletedAt`, log `RESTORE`. Throw `IllegalStateException` if the project isn't currently deleted
- `getWorkload(projectId)` - return a list of developers with their open ticket counts for that project, sorted ascending

---

## Step 6.4 - TicketService, DependencyService & AttachmentService

Implement the core ticket features.

**TicketService** - the most complex service, a few important rules:
- On `createTicket`: if no `assigneeId` is provided, auto-assign to the DEVELOPER in that project with the fewest open tickets. If there's a tie, pick the lowest user ID. Log `AUTO_ASSIGN` with a null actor, then log `CREATE`
- On `updateTicket`: enforce forward-only status transitions (TODO → IN_PROGRESS → IN_REVIEW → DONE). Throw a 400 for any backward move. Also block transitioning to DONE if the ticket still has open (non-DONE, non-deleted) blockers. If priority is manually changed, reset `isOverdue = false`
- `softDeleteTicket` - set `deletedAt`, log `DELETE`
- `restoreTicket` - clear `deletedAt`, log `RESTORE`, throw if not currently deleted

**DependencyService** - validate no self-dependency, same project, no duplicates before saving. Log `CREATE` and `DELETE`.

**AttachmentService** - store file bytes linked to a ticket, log `UPLOAD_ATTACHMENT`.

---

## Step 6.5 - CommentService

Implement `CommentService` with live mention parsing. When a comment is added or updated, parse all `@username` tokens from the content using a regex, then look up each username case-insensitively. Found users get added to `mentionedUsers`, unknown usernames are silently skipped. On `updateComment`, fully replace the existing mention list with a freshly parsed one - don't append to it. Log `ADD_COMMENT` on create and `DELETE` on delete.

---

## Step 6.6 - CsvService & EscalationService

Implement the last two services.

**CsvService** (use Apache Commons CSV):
- `exportTicketsToCsv(projectId, writer)` - write all active tickets for the project to CSV with columns: ID, Title, Description, Status, Priority, Type, AssigneeId
- `importTicketsFromCsv(inputStream, projectId, currentUser)` - parse row by row, apply auto-assignment if no assignee column is provided. If a row fails for any reason, log the error and skip it - don't abort the whole import. Return a `CsvImportResult` with `successfulCount`, `failedCount`, and a list of error messages

**EscalationService** (annotate with `@Scheduled(fixedRate = 60_000)` and `@Transactional`):
- Fetch all overdue non-CRITICAL tickets (due date in the past, status != DONE, not deleted) and promote each one level: LOW → MEDIUM → HIGH → CRITICAL. Set `isOverdue = true` when a ticket reaches CRITICAL. Log `AUTO_ESCALATE` with null actor for each one
- Also fetch any CRITICAL tickets where `isOverdue` is still false and set the flag
- Remember to add `@EnableScheduling` to the main application class

---

## Step 7 - Controllers & Error Handling

Wire up all the REST controllers. Important things to get right:

- Static route segments must be declared before parameterized ones in the same controller - for example `/tickets/deleted` and `/tickets/export` before `/tickets/{id}`, otherwise Spring will match the literal string as an ID
- All write endpoints should log to the audit log via `AuditLogService`
- Create a `GlobalExceptionHandler` with `@RestControllerAdvice` that catches all exceptions and returns a consistent JSON error response with these fields: `timestamp`, `status` (integer), `error` (short label like "Not Found"), `message` (readable explanation), `path` (the request URI), and `details` (list of field errors for validation failures, empty otherwise)
- Status codes to map: 400 for validation/business rule failures, 401 for auth errors, 403 for role errors, 404 for not found, 409 for duplicates and optimistic locking conflicts, 413 for files over the size limit, 500 for unexpected errors

---

## Step 8 - CSV Import/Export

Add two endpoints for bulk ticket operations:

- **Export** - `GET /api/tickets/export?projectId=X` - streams a CSV file as a download (content-type `text/csv`). Include columns for: ID, Title, Description, Status, Priority, Type, AssigneeId
- **Import** - `POST /api/tickets/import` - accepts a `multipart/form-data` request with a `file` field and a `projectId` field. Read the CSV row by row. If a row fails validation or parsing, log the error and skip it - don't abort the whole import. Return a JSON summary object with `successfulCount`, `failedCount`, and a `List<String> errors` with one message per failed row

Use Apache Commons CSV for parsing. Don't load the entire file into memory at once.

---

## Step 9 - Unit Tests

Write unit tests for all the service classes. Use JUnit 5 and Mockito - no Spring context, no database, mock all dependencies with `@Mock` and `@InjectMocks`. Cover the important edge cases:

- **Auto-assignment**: what happens when there are no developers? What when multiple developers are tied at 0 tickets? The one with the lowest ID should win
- **Status transitions**: verify that TODO → IN_PROGRESS works, IN_PROGRESS → TODO throws, and DONE → anything throws
- **Dependency blocking**: verify that trying to close a ticket with open blockers returns a 400
- **Mention parsing**: `@Alice` should match a user registered as `alice` (case-insensitive). Unknown usernames should be silently skipped
- **Escalation**: LOW → MEDIUM → HIGH → CRITICAL. A CRITICAL ticket should stay CRITICAL and not throw. A non-overdue ticket with a future due date should not be escalated

Use descriptive method names like `createTicket_withNoAssignee_shouldAutoAssignToLeastBusyDeveloper`.

---

## Step 10 - Bug Fixes

Found a few issues after reviewing the code and running some manual tests:

- **Mentions are case-sensitive** - if a user registered as `alice` and a comment says `@Alice`, the mention isn't found. Fix the lookup to be case-insensitive. Also, when a comment is *edited*, the mentions list doesn't get updated - it still shows the original mentions. The full mention list needs to be recalculated on every update
- **Escalation skips HIGH → CRITICAL** - checked the scheduler and HIGH tickets never get promoted to CRITICAL. Probably an off-by-one or a missing enum case. Also, the escalation logic is currently embedded in the scheduler class itself, which makes it impossible to unit test directly - extract it into an `EscalationService` with a method the scheduler just calls
- **Auto-assignment is non-deterministic on ties** - when all developers have 0 tickets, the assignment could go to any of them depending on query order. Add an `ORDER BY id ASC` to the query so it always picks the same developer
- **Auto-assignment missing audit log** - when a ticket gets auto-assigned, no audit log entry is written. Add an `AUTO_ASSIGN` log entry after the assignment
- **Empty string passes PATCH validation** - sending `{"title": ""}` in a PATCH request goes through even though an empty title shouldn't be valid. The validation needs to block empty strings but still allow `null` (so that omitting a field from the request works correctly for partial updates)

---

## Step 11 - Circular Dependencies & Cascade

Two more things that need to be added:

**Cycle detection in dependencies** - right now the system only checks for direct self-dependencies (ticket blocking itself), but it doesn't catch transitive cycles. For example: A blocks B, B blocks C, then adding C blocks A should be rejected - but currently it goes through. Before saving any new dependency, run a BFS (breadth-first search) starting from the new `blockedBy` ticket and walking up the chain. If you reach the ticket that's trying to add the dependency, reject it with a 400.

**Cascade soft delete for projects** - right now soft-deleting a project only marks the project itself as deleted. Its tickets stay active. The correct behavior (and once this is implemented, add the matching unit tests to `ProjectServiceTest`):

- When a project is soft-deleted, all its active (non-deleted) tickets should be soft-deleted in the same transaction. Store the current timestamp in their `deletedAt` fields
- When the project is restored, only tickets that were deleted *as part of that project deletion* should be restored. Tickets that were individually deleted before the project deletion should stay deleted. One way to track this: store the project's `deletedAt` timestamp in a `projectDeletedAt` field on each ticket that was cascaded, and use that to distinguish them during restore
