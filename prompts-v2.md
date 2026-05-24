# IssueFlow - AI Prompts Log

## Tools Used

- **Claude Code (claude-sonnet-4-6)** - primary coding assistant. Responsible for all code generation, implementation, refactoring, bug fixes, and running tests directly in the terminal via the CLI.
- **Google Gemini 2.5 Pro & Flash** - used in early and ongoing phases for understanding requirements, brainstorming architecture, generating and refining prompts, and answering conceptual questions before diving into implementation.

---

## Working Methodology

Claude Code was used via a standard subscription inside Visual Studio Code, with direct access to the terminal and the full project directory throughout the entire development session.

The workflow followed a structured plan prepared during Step 0 - before writing any code, the agent was asked to analyze the full assignment and produce a 3-day implementation plan. That plan served as the backbone for all subsequent prompts, which were written and refined based on it rather than improvised on the fly.

For each completed step, the topic was researched independently and the agent was asked to produce organized documentation alongside the code - making it possible to understand what was generated and why before moving to the next layer.

**Challenge - context window exhaustion:**
The main technical challenge was that conversation context filled up quickly during long sessions. The solution was to request a structured handoff summary before closing each session:

*"We are about to close this conversation and open a fresh one. Please generate a `project_status.md` file containing: a short description of the current architecture, which components and code files have been fully written (including key function signatures), important technical decisions we made, and the exact list of next steps to continue the next vertical slice."*

**Code review:**
Every piece of generated code went through a review pass - both by the agent (asked to self-review for bugs and edge cases) and manually. Corrections were applied iteratively before moving forward.

---

## Step 0 - Project Analysis & Implementation Planning

**Assignment Alignment:**
Initial orientation phase - understanding the full scope before writing any code.

**Engineering Intent:**
Avoid building blind. Produce a complete picture of what exists, what needs to be built, and in what order before starting implementation.

**Prompt:**
> I have a homework assignment for the AT&T TDP 2026 program in my current working directory - a ticket management backend called IssueFlow.
>
> Read and analyze all project files (the PDF requirements, both skeleton projects, READMEs, pom.xml, package.json, source files, and configuration). Then:
> 1. Explain the assignment - what needs to be built, what APIs are required, what special features are needed (soft delete, auto-assignment, auto-escalation, mentions, JWT auth, CSV import/export), and what is already provided vs. what needs to be implemented.
> 2. Create a 3-day implementation plan ordered by dependencies, where each day focuses on a distinct layer or feature set. I'll implement in Java (Spring Boot).
> 3. Generate a PDF guide in English covering: a plain-English explanation of the assignment, full system architecture diagram, package/folder hierarchy, all required API endpoints, step-by-step build order, and key Spring Boot technical tips.

**Key Design Decisions:**
- Java (Spring Boot) chosen as the implementation language
- Vertical slicing strategy adopted - one feature end-to-end before the next
- 3-day plan with clear daily boundaries to manage scope

**Scope Control:**
- No code written at this stage
- Security, CSV, and escalation intentionally deferred to later steps

**Validation:**
- Plan reviewed against the assignment PDF before implementation began

---

## Step 0.5 - Architecture Strategy Exploration (Vertical Slicing)

**Assignment Alignment:**
First vertical slice - User Management and Authentication.

**Engineering Intent:**
Establish the project skeleton and validate the package structure before adding any business logic.

**Prompt:**
> We're building the IssueFlow backend using the skeleton in `issueflow-java/`, following a "Vertical Slicing" strategy - one feature end-to-end before moving to the next.
>
> Start with the first slice: User Management & Authentication. Generate code ONLY for:
> 1. `entity/User.java` - JPA entity mapped to PostgreSQL.
> 2. `repository/UserRepository.java` - Spring Data JPA interface.
> 3. `dto/` - Request/Response DTOs for user registration.
> 4. `service/UserService.java` - business logic for user creation (password hashing placeholder).
> 5. `controller/UserController.java` - REST endpoints for user registration.
>
> Don't touch Project, Ticket, or Comments yet. Don't write the full JWT security filter - focus on the User CRUD flow first.

**Key Design Decisions:**
- Package-by-layer structure under `com.att.tdp.issueflow`
- PostgreSQL via `compose.yml` for local development
- JWT security intentionally deferred to Step 5

**Scope Control:**
- Project, Ticket, and Comment layers explicitly excluded
- Full JWT filter not written yet

**Validation:**
- Project compiled with the skeleton in place before proceeding

---

## Step 1 - Database Schema

**Assignment Alignment:**
Persistence foundation - all 8 tables required by the assignment.

**Engineering Intent:**
Define the full schema upfront so that all entities, relationships, and constraints are locked in before any JPA code is written.

**Prompt:**
> Generate the full `schema.sql` with all 8 required tables in PostgreSQL syntax:
> 1. **`users`**: id, username, email, full_name, role (`'DEVELOPER'` or `'ADMIN'`), password.
> 2. **`projects`**: id, name, description, owner_id (FK to users), deleted_at (nullable; null = active).
> 3. **`tickets`**: id, title, status, priority, type, project_id (FK), assignee_id (FK, nullable), due_date, deleted_at (nullable), is_overdue (BOOLEAN NOT NULL DEFAULT FALSE).
> 4. **`comments`**: id, ticket_id (FK), author_id (FK), content, created_at.
> 5. **`comment_mentions`**: comment_id (FK), user_id (FK). Join table for @username references.
> 6. **`ticket_dependencies`**: ticket_id (FK), blocked_by_id (FK). Ticket A is blocked by Ticket B.
> 7. **`attachments`**: id, ticket_id (FK), filename, content_type, data (`bytea`).
> 8. **`audit_logs`**: id, action (CHECK constraint), entity_type, entity_id, performed_by (FK, nullable for system actions), timestamp.
>
> Add PK/FK constraints and referential integrity. Use `TIMESTAMP` for date/time columns. Output raw SQL only.

**Key Design Decisions:**
- `deleted_at` as nullable timestamp for soft delete (null = active)
- `is_overdue` as a real `BOOLEAN NOT NULL DEFAULT FALSE` column - not `@Transient`
- `comment_mentions` as a join table for the many-to-many mention relationship
- `performed_by` nullable in audit_logs to support system-initiated actions

**Scope Control:**
- No JPA annotations at this stage
- No application code generated

**Validation:**
- Schema applied to local PostgreSQL via Docker Compose before proceeding

---

## Step 2 - Entities & Enums

**Assignment Alignment:**
JPA model layer - all enums and entities across the full system.

**Engineering Intent:**
Generate all entities in a single step to avoid incremental mismatches between entity definitions and the schema from Step 1.

**Prompt:**
> Generate all Enums under `com.att.tdp.issueflow.enums` and all JPA Entities under `com.att.tdp.issueflow.entity`. Use Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`) and standard JPA annotations. Enum fields must use `@Enumerated(EnumType.STRING)`.
>
> **Enums:** `UserRole` (DEVELOPER, ADMIN), `TicketStatus` (TODO, IN_PROGRESS, IN_REVIEW, DONE), `TicketPriority` (LOW, MEDIUM, HIGH, CRITICAL), `TicketType` (BUG, FEATURE, TECHNICAL).
>
> **Entities:**
> 1. **User.java** - id (PK), username (unique), email (unique), fullName, role, password.
> 2. **Project.java** - id, name, description, deletedAt (nullable). `@ManyToOne` owner with `FetchType.LAZY`.
> 3. **Ticket.java** - id, title, status, priority, type, dueDate, deletedAt, isOverdue as `@Column(name = "is_overdue", nullable = false)` - must be a real DB column, NOT `@Transient`. `@ManyToOne` project and nullable assignee. Add `@Version` for optimistic locking.
> 4. **Comment.java** - id, content, createdAt. `@ManyToOne` ticket and author. `@ManyToMany` mentionedUsers via `comment_mentions` join table. Add `@Version`.
> 5. **TicketDependency.java** - Composite PK via `@IdClass(TicketDependencyId.class)`. `@ManyToOne` ticket and blockedBy.
> 6. **Attachment.java** - id, filename, contentType, data (`@Column(columnDefinition = "BYTEA")`). `@ManyToOne` ticket.
> 7. **AuditLog.java** - id, action, entityType, entityId, timestamp. Nullable `@ManyToOne` performedBy.
>
> No DTOs, Repositories, Services, or Controllers at this stage. Add `@JsonIgnoreProperties` where needed to prevent circular serialization.

**Key Design Decisions:**
- `TicketDependency` modeled as its own entity with `@IdClass` - enables direct querying, logging, and cycle detection
- `@Version` on `Ticket` and `Comment` for optimistic locking
- `FetchType.LAZY` on all `@ManyToOne` associations

**Scope Control:**
- No DTOs, Repositories, Services, or Controllers at this stage

**Validation:**
- Hibernate schema validation run against the Step 1 SQL

---

## Step 3 - DTOs

**Assignment Alignment:**
API contract layer - all request and response shapes for every endpoint.

**Engineering Intent:**
Keep entities off the wire entirely. All API responses use dedicated DTOs, preventing accidental exposure of internal fields.

**Prompt:**
> Generate all DTOs in `com.att.tdp.issueflow.dto`. Use Lombok. Use `jakarta.validation.constraints.*` on all Request objects. Never expose JPA entities in DTOs - use IDs instead.
>
> **User:** `CreateUserRequest` (username, email, fullName, role, password - all `@NotBlank`/`@NotNull`), `UpdateUserRequest` (all optional for PATCH), `UserResponse` (id, username, email, fullName, role - no password).
>
> **Auth:** `LoginRequest` (username, password), `LoginResponse` (token).
>
> **Project:** `CreateProjectRequest` (name `@NotBlank`, description, ownerId `@NotNull`), `UpdateProjectRequest` (optional), `ProjectResponse`, `WorkloadEntry` (userId, username, openTicketCount).
>
> **Ticket:** `CreateTicketRequest` (title `@NotBlank`, priority/type `@NotNull`, projectId `@NotNull`, assigneeId and dueDate nullable), `UpdateTicketRequest` (all optional for PATCH), `AddDependencyRequest` (blockedBy `@NotNull`), `TicketResponse`.
>
> **Comment:** `CreateCommentRequest` (content `@NotBlank`), `UpdateCommentRequest`, `CommentResponse` (id, ticketId, authorId, content, createdAt, `List<MentionedUserDto>`), `MentionedUserDto` (id, username, fullName - include static `from(User)` factory), `MentionsPageResponse`.
>
> **Other:** `CsvImportResult` (successfulCount, failedCount, errors `List<String>`).

**Key Design Decisions:**
- Passwords never appear in any response DTO
- `MentionedUserDto` includes a static `from(User)` factory method
- All PATCH request DTOs use nullable fields to support partial updates

**Scope Control:**
- No service or controller logic generated at this stage

**Validation:**
- All DTOs compiled with Lombok annotation processing verified

---

## Step 4 - Repositories

**Assignment Alignment:**
Data access layer - all queries needed by the services in Step 6.

**Engineering Intent:**
Use Spring Data derived method names wherever possible, falling back to `@Query` (JPQL) only when necessary.

**Prompt:**
> Generate all Spring Data JPA repository interfaces in `com.att.tdp.issueflow.repository`. Each extends `JpaRepository`. Use derived method names where possible; use `@Query` (JPQL) only when necessary.
>
> 1. **UserRepository**: `findByUsername`, `findByUsernameIgnoreCase`, `existsByUsername`, `existsByEmail`.
> 2. **ProjectRepository**: `findByDeletedAtIsNull`, `findByDeletedAtIsNotNull`, `findByIdAndDeletedAtIsNull`.
> 3. **TicketRepository**: `findByProjectIdAndDeletedAtIsNull`, `findByProjectIdAndDeletedAtIsNotNull`, `findByIdAndDeletedAtIsNull`. Plus `@Query` to count open tickets per assignee in a project. Plus `@Query` for overdue non-CRITICAL tickets. Plus `@Query` for already-CRITICAL overdue tickets where `isOverdue = false`.
> 4. **CommentRepository**: `findByTicketId`. Plus `@Query` to find comments mentioning a specific user with `Pageable`.
> 5. **AuditLogRepository**: `findByEntityTypeAndEntityIdOrderByTimestampDesc`.
> 6. **AttachmentRepository**: `findByTicketId`.
> 7. **TicketDependencyRepository**: `findByTicketId`, `existsByTicketIdAndBlockedById`.

**Key Design Decisions:**
- Overdue ticket queries expressed in JPQL filtering on `dueDate < NOW`, `status != DONE`, `deletedAt IS NULL`
- Workload count query returns `List<Object[]>` with `[assigneeId, count]` pairs

**Scope Control:**
- No service logic implemented at this stage
- Native SQL avoided in favor of JPQL throughout

**Validation:**
- Repository methods verified against entity field names before service implementation

---

## Step 5 - JWT Security Layer

**Assignment Alignment:**
Authentication and authorization - stateless JWT with role-based access control.

**Engineering Intent:**
Establish security before any business endpoint is exposed so that every endpoint added afterward is protected from the start.

**Prompt:**
> Add Spring Security with stateless JWT authentication. Provide pom.xml snippets for `spring-boot-starter-security` and JJWT. Generate these classes in `com.att.tdp.issueflow.security`:
>
> 1. **`JwtUtil.java`** - Secret key from `@Value("${jwt.secret}")`. Methods: `generateToken(username)`, `extractUsername(token)`, `validateToken(token, userDetails)`. Expiration: 24 hours.
> 2. **`JwtAuthFilter.java`** - Extends `OncePerRequestFilter`. Extracts `Authorization: Bearer` header, validates via `JwtUtil`, sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`. Skips if token is in logout blocklist.
> 3. **`SecurityConfig.java`** - Stateless sessions, CSRF disabled. Public: `POST /api/users`, `POST /api/auth/login`. Everything else requires authentication. Registers `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`.
> 4. **`CustomUserDetailsService.java`** - Implements `UserDetailsService`. Loads user by username, maps role to `GrantedAuthority`.

**Key Design Decisions:**
- In-memory token blocklist for logout invalidation - stolen tokens cannot be reused after logout
- Secret key injected via `@Value` - never hardcoded
- Only registration and login are public routes

**Scope Control:**
- Refresh tokens not implemented
- Token persistence not required

**Validation:**
- Login and protected endpoint access tested via curl before proceeding
- Logout verified to invalidate the token server-side

---

## Step 6 - Services (Business Logic)

**Assignment Alignment:**
Full business logic layer across all features.

**Engineering Intent:**
Implement services in dependency order: audit logging first (needed by everything else), then users and auth, then projects, then tickets and dependencies, then comments, then CSV and escalation.

**Prompt (6.1):**
> Let's implement the foundational logging layer first: `AuditLogService.java`. Single public method: `log(String action, String entityType, Long entityId, User performedBy)`. Sets timestamp to `LocalDateTime.now()`, saves to DB. Handle null `performedBy` for system actions.

**Prompt (6.2):**
> Next, let's implement `UserService.java` and `AuthService.java`.
>
> **UserService:** Registration: validate username/email uniqueness (throw `ConflictException` if taken), hash password, save, log `"CREATE"`. Also: `getAllUsers()`, `getUserById(id)`, `updateUser(id, request)` (partial update - skip email uniqueness check if unchanged), `deleteUser(id)`, `getUserMentions(userId, page, pageSize)`.
>
> **AuthService:** `login(LoginRequest)`: authenticate via `AuthenticationManager`, return JWT. `logout(token)`: add to in-memory blocklist. `getMe(username)`: return current user as `UserResponse`.

**Prompt (6.3):**
> Next, let's implement `ProjectService.java`: `createProject` (validate owner exists, save, log `"CREATE"`), `getProjectById`, `getAllActiveProjects`, `getDeletedProjects`, `updateProject` (partial - only apply non-null fields), `softDeleteProject` (set `deletedAt = now()`, log `"DELETE"`), `restoreProject` (clear `deletedAt`, log `"RESTORE"`, throw `IllegalStateException` if not deleted), `getWorkload(projectId)` (sorted ascending by `openTicketCount`).

**Prompt (6.4):**
> Next, let's implement `TicketService.java`, `DependencyService.java`, and `AttachmentService.java`.
>
> **TicketService:** `createTicket`: if no `assigneeId` provided, auto-assign to the DEVELOPER with the fewest open tickets in the project (tie-break: lowest user ID). Log `"AUTO_ASSIGN"` with null actor if triggered. Log `"CREATE"`. `updateTicket`: partial update. Enforce forward-only transitions. Block DONE transition if there are unresolved blockers. Manual priority change resets `isOverdue = false`. `softDeleteTicket`, `restoreTicket`.
>
> **DependencyService:** `addDependency` (validate no self-dependency, same project, no duplicates), `removeDependency`, `getDependencies(ticketId)`.
>
> **AttachmentService:** store files as `byte[]` linked to a ticket. Log `"UPLOAD_ATTACHMENT"`.

**Prompt (6.5):**
> Next, let's implement `CommentService.java`: `addComment`: parse `content` with regex `@(\w+)`. For each match, look up via `findByUsernameIgnoreCase` - add found users to `mentionedUsers`, silently skip unknown usernames. Save, log `"ADD_COMMENT"`. `updateComment`: update content AND re-parse mentions - replace existing `mentionedUsers` with freshly parsed results. `deleteComment`.

**Prompt (6.6):**
> Finally, `CsvService.java` and `EscalationService.java`.
>
> **CsvService:** `exportTicketsToCsv(projectId, writer)` and `importTicketsFromCsv(inputStream, projectId, currentUser)` with per-row error tolerance. Return `CsvImportResult`.
>
> **EscalationService:** `@Scheduled(fixedRate = 60_000)` with `@Transactional`. Step 1: fetch overdue non-CRITICAL tickets, promote one level (LOW→MEDIUM, MEDIUM→HIGH, HIGH→CRITICAL), set `isOverdue = true` when reaching CRITICAL. Step 2: fetch already-CRITICAL overdue tickets where `isOverdue = false` - set flag, log. Add `@EnableScheduling` to `IssueFlowApplication`.

**Key Design Decisions:**
- Auto-assignment logs `"AUTO_ASSIGN"` with null actor when triggered
- Manual priority change in `updateTicket` resets `isOverdue = false`
- `DONE` transition blocked if unresolved blockers exist
- Escalation split into two phases: promote non-CRITICAL tickets, then flag already-CRITICAL ones

**Scope Control:**
- No controller layer added at this stage
- Attachment storage in-database (`BYTEA`) - no external file system

**Validation:**
- Each service verified against entity and repository contracts before proceeding

---

## Step 7 - Controllers & Exception Handling

**Assignment Alignment:**
Full REST API surface and centralized error handling.

**Engineering Intent:**
Generate all controllers in one step to ensure consistent conventions. Centralize all exception mapping in a single `@RestControllerAdvice`.

**Prompt:**
> Generate all REST Controllers in `com.att.tdp.issueflow.controller` and the exception handler in `com.att.tdp.issueflow.exception`.
>
> **General requirements:** `@RestController`, all endpoints prefixed `/api`, constructor injection via `@RequiredArgsConstructor`, `@Valid` on all request bodies, `@AuthenticationPrincipal` to resolve the current user, standard HTTP codes (`201` for creates, `204` for deletes).
>
> **Route ordering:** in `ProjectController` and `TicketController`, static paths (`/deleted`, `/export`) must be declared BEFORE parameterized paths (`/{id}`).
>
> 1. **`AuthController`** - `POST /login`, `POST /logout`, `GET /me`.
> 2. **`UserController`** - `POST` (public), `GET`, `GET /{id}`, `PATCH /{id}`, `DELETE /{id}`, `GET /{id}/mentions`.
> 3. **`ProjectController`** - full CRUD + `/deleted`, `/{id}/restore`, `/{id}/workload`.
> 4. **`TicketController`** - full CRUD + `/deleted`, `/export`, `/{id}/restore`, dependencies, `/import`.
> 5. **`CommentController`** - CRUD under `/tickets/{ticketId}/comments`.
> 6. **`AttachmentController`** - upload and download under `/tickets/{ticketId}/attachments`.
> 7. **`AuditLogController`** - `GET /audit-logs` with optional `entityType`, `entityId`, `action` params.
> 8. **`GlobalExceptionHandler`** - structured `ErrorResponse` (timestamp, status, error, message, path, details). Map every exception: not-found, conflict, bad input, access denied, file size exceeded, catch-all.

**Key Design Decisions:**
- `path` field added to `ErrorResponse` via `HttpServletRequest.getRequestURI()`
- Field-level validation errors expanded into the `details` array on `400` responses
- `@PreAuthorize("hasRole('ADMIN')")` on all admin-only endpoints with `@EnableMethodSecurity`

**Scope Control:**
- Swagger/OpenAPI documentation not included

**Validation:**
- All endpoints verified via `test-api.sh` against a running local instance

---

## Step 8 - CSV Import/Export

**Assignment Alignment:**
Bulk data transfer requirement - bidirectional CSV support for tickets.

**Engineering Intent:**
Implement export as a streaming response and import with per-row error isolation so that a single bad row does not abort the entire batch.

**Prompt:**
> Generate or verify `CsvService.java` and the corresponding endpoints in `TicketController`.
>
> **Export:** `GET /api/tickets/export?projectId=X` - set `Content-Type: text/csv` and `Content-Disposition: attachment; filename="tickets.csv"`. Stream all active tickets via Apache Commons CSV.
>
> **Import:** `POST /api/tickets/import?projectId=X` - accepts `multipart/form-data`. Parse row by row with per-row error isolation. Return:
> ```json
> { "successfulCount": N, "failedCount": M, "errors": ["Row 2: Missing title", "Row 5: Invalid priority"] }
> ```

**Key Design Decisions:**
- Apache Commons CSV used for both read and write
- Auto-assignment applied during import if no assignee column present
- Failed rows logged in the response but do not roll back successful ones

**Scope Control:**
- Import limited to ticket creation - updates not supported via CSV

**Validation:**
- Import tested with intentionally malformed rows to confirm error isolation

---

## Step 9 - Unit Tests

**Assignment Alignment:**
Testing requirement - full unit test coverage for all core service classes.

**Engineering Intent:**
No Spring context. All tests use `@ExtendWith(MockitoExtension.class)` with mocked dependencies. Focus on business logic correctness, edge cases, and error paths.

**Prompt:**
> Write unit tests using JUnit 5 and Mockito. No Spring context - all dependencies mocked with `@ExtendWith(MockitoExtension.class)`.
>
> 1. **`UserServiceTest`**: registration success (password encoded, audit logged, plain-text never saved); duplicate username/email → `ConflictException`. `getAllUsers`, `getUserById` (success + 404), `updateUser`, `deleteUser`.
> 2. **`TicketServiceTest`**: auto-assignment picks dev with fewest open tickets; all devs at zero → lowest ID wins; no devs → null assignee; explicit assigneeId skips auto-assign; unknown assigneeId → 404. Soft-delete sets `deletedAt`. `restoreTicket`; backward status → throws; DONE with open blockers → throws; manual priority change resets `isOverdue`.
> 3. **`CommentServiceTest`**: mention parsing extracts multiple `@username` matches. Unknown mention → skipped silently. No `@` in content → repository never called. Ticket not found → throws. `updateComment` re-parses mentions.
> 4. **`EscalationServiceTest`**: LOW→MEDIUM (`isOverdue` stays false); MEDIUM→HIGH (stays false); HIGH→CRITICAL (`isOverdue = true`); already-CRITICAL without flag → flag set, priority unchanged; no overdue tickets → no saves, no logs.
> 5. **`ProjectServiceTest`**: `createProject` success; owner not found → 404. `softDeleteProject` + audit. `restoreProject`; already-active → `IllegalStateException`.
> 6. **`DependencyServiceTest`**: `addDependency` success; self-dependency → `IllegalArgumentException`; different projects → `IllegalArgumentException`; duplicate → `ConflictException`. `removeDependency`; not found → `ResourceNotFoundException`.
>
> Use descriptive test names (e.g., `createTicket_autoAssignment_selectsDevWithFewestOpenTickets`).

**Key Design Decisions:**
- H2 in-memory database used for application context smoke test only
- Test names follow the pattern `methodName_condition_expectedOutcome`

**Scope Control:**
- No controller-layer or integration tests

**Validation:**
- `./mvnw test` → `Tests run: 69, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS`

---

## Step 10 - Refinements & Bug Fixes

**Assignment Alignment:**
Correctness and robustness issues identified during end-to-end testing.

**Engineering Intent:**
Fix behavioral gaps found during manual testing before final submission. Each fix isolated to avoid regressions.

**Prompt:**
> I've been testing everything end-to-end and ran into a few things that don't behave the way I expected. Can you go through these and fix them?
>
> **10.1 - Mentions not working when capitalized:** I created a user with the username "alice", then wrote `@Alice` in a comment and the mention wasn't saved. Make it work regardless of capitalization. Also, editing a comment doesn't update the mention list - it should re-parse.
>
> **10.2 - Escalation scheduler isn't promoting HIGH tickets:** HIGH priority tickets never get promoted to CRITICAL. LOW and MEDIUM go up fine. Also, the logic is all inside the scheduler directly - there's no service class for it. Can you move the logic to a service so I can test it properly? And `isOverdue` doesn't stay saved after the scheduler sets it.
>
> **10.3 - Auto-assignment has a few gaps:** No audit log entry when a ticket gets auto-assigned. The workload endpoint order is different every time - needs consistent ordering. When all developers have 0 open tickets, the assignment is arbitrary - should there be a rule for that?
>
> **10.4 - Empty string slips through PATCH validation:** I sent a PATCH with `"title": ""` and it saved. An empty title shouldn't be valid. But adding `@NotBlank` broke partial updates since it also rejects null - need a way to allow null but reject empty strings.

**Key Design Decisions:**
- Escalation business logic lives in `EscalationService`, not in the scheduler directly
- Tie-breaking by lowest user ID ensures deterministic auto-assignment
- Null allowed in PATCH fields; empty string rejected via custom constraint

**Scope Control:**
- Only the four reported issues addressed

**Validation:**
- All 69 unit tests pass after fixes
- Each scenario manually re-tested via `test-api.sh`

---

## Step 11 - System-Level Improvements

**Assignment Alignment:**
Advanced correctness requirements: cycle detection and cascaded soft delete.

**Engineering Intent:**
Address two structural gaps not caught during initial implementation: transitive dependency cycles and orphaned ticket records after project soft delete.

**Prompt:**
> **11.1 - BFS Circular Dependency Detection:** `DependencyService.addDependency` validates self-reference, same-project, and duplicates - but does NOT detect transitive cycles. A chain like A ← B ← C and then C ← A creates an unresolvable deadlock.
>
> Add `findBlockerIdsByTicketId(Long ticketId)` to `TicketDependencyRepository`. Add a private `wouldCreateCycle(Long ticketId, Long newBlockerId)` BFS method to `DependencyService`: start from `newBlockerId`, follow the existing blocker chain, return true if `ticketId` is reached. Throw `IllegalArgumentException` if a cycle is detected. Add test: `addDependency_wouldCreateCycle_throwsIllegalArgumentException`.
>
> **11.2 - Cascaded Soft Delete for Projects:** `softDeleteProject` sets `deletedAt` on the project but leaves all its tickets active. `restoreProject` only restores the project record.
>
> In `softDeleteProject`: after saving the project, fetch all active tickets and set their `deletedAt` to the same timestamp. In `restoreProject`: fetch all tickets deleted with that same timestamp, clear their `deletedAt`. Add tests: `softDeleteProject_cascadesToActiveTickets` and `restoreProject_cascadesToDeletedTickets`.

**Key Design Decisions:**
- BFS chosen for cycle detection - simpler to reason about and avoids stack overflow on deep chains
- Cascade uses the same timestamp on project and tickets to distinguish project-cascade deletions from individual ticket deletions

**Scope Control:**
- Cycle detection limited to the dependency graph only

**Validation:**
- All three new tests added and passing
- Full test suite: 69 tests, 0 failures
