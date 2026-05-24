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
The main technical challenge was that the conversation context filled up quickly during long implementation sessions. My solution was to request a structured handoff summary before ending each session, in this format:

> "We are about to close this conversation and open a fresh one. Please generate a `project_status.md` file containing: a short description of the current architecture; which components and code files have been fully written (including key function signatures); important technical decisions we made; and the exact list of next steps to continue the next vertical slice."

This allowed each new session to resume from a precise, well-defined state without losing progress.

**Code review:**
Every piece of generated code went through a review pass - both by the agent (asked to self-review for bugs, edge cases, and consistency) and by me. Corrections and improvements were applied iteratively before moving forward.

---

This file documents the prompts used throughout the development of the IssueFlow backend. Prompts are listed in order of use, grouped by development phase.

---

## Prompt 0 - Project Analysis & Implementation Planning

I have a homework assignment for the AT&T TDP 2026 program in my current working directory - a ticket management backend called IssueFlow.

Read and analyze all project files (the PDF requirements, both skeleton projects, READMEs, pom.xml, package.json, source files, and configuration). Then:

1. Explain the assignment - what needs to be built, what APIs are required, what special features are needed (soft delete, auto-assignment, auto-escalation, mentions, JWT auth, CSV import/export), and what is already provided vs. what needs to be implemented.
2. Create a 3-day implementation plan ordered by dependencies, where each day focuses on a distinct layer or feature set. I'll implement in Java (Spring Boot).
3. Generate a PDF guide in English covering: a plain-English explanation of the assignment, full system architecture diagram, package/folder hierarchy, all required API endpoints, step-by-step build order, and key Spring Boot technical tips.

---

## Prompt 0.5 - Architecture Strategy Exploration (Vertical Slicing)

We're building the IssueFlow backend using the skeleton in `issueflow-java/`, following a "Vertical Slicing" strategy - one feature end-to-end before moving to the next.

Start with the first slice: User Management & Authentication. Generate code ONLY for:

1. `entity/User.java` - JPA entity mapped to PostgreSQL.
2. `repository/UserRepository.java` - Spring Data JPA interface.
3. `dto/` - Request/Response DTOs for user registration.
4. `service/UserService.java` - business logic for user creation (password hashing placeholder).
5. `controller/UserController.java` - REST endpoints for user registration.

Don't touch Project, Ticket, or Comments yet. Don't write the full JWT security filter - focus on the User CRUD flow first.

---

## Step 1 - Database Schema

Generate the full `schema.sql` with all 8 required tables in PostgreSQL syntax:

1. **`users`**: id, username, email, full_name, role (`'DEVELOPER'` or `'ADMIN'`), password.
2. **`projects`**: id, name, description, owner_id (FK to users), deleted_at (nullable; null = active).
3. **`tickets`**: id, title, status (`'TODO'`, `'IN_PROGRESS'`, `'IN_REVIEW'`, `'DONE'`), priority (`'LOW'`, `'MEDIUM'`, `'HIGH'`, `'CRITICAL'`), type, project_id (FK), assignee_id (FK, nullable), due_date, deleted_at (nullable), is_overdue (BOOLEAN NOT NULL DEFAULT FALSE).
4. **`comments`**: id, ticket_id (FK), author_id (FK), content, created_at.
5. **`comment_mentions`**: comment_id (FK), user_id (FK). Join table for @username references.
6. **`ticket_dependencies`**: ticket_id (FK), blocked_by_id (FK). Ticket A is blocked by Ticket B.
7. **`attachments`**: id, ticket_id (FK), filename, content_type, data (`bytea`).
8. **`audit_logs`**: id, action (CHECK constraint), entity_type, entity_id, performed_by (FK, nullable for system actions), timestamp.

Add PK/FK constraints and referential integrity. Use `TIMESTAMP` for date/time columns. Output raw SQL only.

---

## Step 2 - Entities & Enums

We successfully completed the database schema (Step 1). Now, we are moving to **Step 2: Creating all Enums and JPA Entities** under the packages `com.att.tdp.issueflow.enums` and `com.att.tdp.issueflow.entity`.

Generate all Enums under `com.att.tdp.issueflow.enums` and all JPA Entities under `com.att.tdp.issueflow.entity`. Use Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`) and standard JPA annotations. Enum fields must use `@Enumerated(EnumType.STRING)`.

**Enums:** `UserRole` (DEVELOPER, ADMIN), `TicketStatus` (TODO, IN_PROGRESS, IN_REVIEW, DONE), `TicketPriority` (LOW, MEDIUM, HIGH, CRITICAL), `TicketType` (BUG, FEATURE, TECHNICAL).

**Entities:**
1. **User.java** - id (PK), username (unique), email (unique), fullName, role, password.
2. **Project.java** - id, name, description, deletedAt (nullable). `@ManyToOne` owner with `FetchType.LAZY`.
3. **Ticket.java** - id, title, status, priority, type, dueDate, deletedAt, isOverdue as `@Column(name = "is_overdue", nullable = false)` - must be a real DB column, NOT `@Transient`. `@ManyToOne` project and nullable assignee. Add `@Version` for optimistic locking.
4. **Comment.java** - id, content, createdAt. `@ManyToOne` ticket and author. `@ManyToMany` mentionedUsers via `comment_mentions` join table. Add `@Version`.
5. **TicketDependency.java** - Composite PK via `@IdClass(TicketDependencyId.class)`. `@ManyToOne` ticket and blockedBy. Also generate `TicketDependencyId.java` (Serializable, fields: Long ticket, Long blockedBy).
6. **Attachment.java** - id, filename, contentType, data (`@Column(columnDefinition = "BYTEA")`). `@ManyToOne` ticket.
7. **AuditLog.java** - id, action, entityType, entityId, timestamp. Nullable `@ManyToOne` performedBy (null for system actions).

No DTOs, Repositories, Services, or Controllers at this stage. Add `@JsonIgnoreProperties` where needed to prevent circular serialization.

---

## Step 3 - DTOs (Request & Response Objects)

We have successfully completed Step 1 (DB Schema) and Step 2 (Entities). Now, please execute **Step 3: All DTOs**.

Generate all DTOs in `com.att.tdp.issueflow.dto`. Use Lombok. Use `jakarta.validation.constraints.*` on all Request objects. Never expose JPA entities in DTOs - use IDs instead.

**User:** `CreateUserRequest` (username, email, fullName, role, password - all `@NotBlank`/`@NotNull`), `UpdateUserRequest` (all optional for PATCH), `UserResponse` (id, username, email, fullName, role - no password).

**Auth:** `LoginRequest` (username, password), `LoginResponse` (token).

**Project:** `CreateProjectRequest` (name `@NotBlank`, description, ownerId `@NotNull`), `UpdateProjectRequest` (name, description - optional), `ProjectResponse` (id, name, description, ownerId), `WorkloadEntry` (userId, username, openTicketCount).

**Ticket:** `CreateTicketRequest` (title `@NotBlank`, priority/type `@NotNull`, projectId `@NotNull`, assigneeId and dueDate nullable), `UpdateTicketRequest` (all optional for PATCH), `AddDependencyRequest` (blockedBy `@NotNull`), `TicketResponse` (id, title, status, priority, type, projectId, assigneeId, dueDate, isOverdue).

**Comment:** `CreateCommentRequest` (authorId `@NotNull`, content `@NotBlank`), `UpdateCommentRequest` (content `@NotBlank`), `CommentResponse` (id, ticketId, authorId, content, createdAt, `List<MentionedUserDto>`), `MentionedUserDto` (id, username, fullName - include static `from(User)` factory), `MentionsPageResponse` (content, pageNumber, totalElements, totalPages).

**Other:** `CsvImportResult` (successfulCount, failedCount, errors `List<String>`).

---

## Step 4 - Repositories (Data Access Layer)

We have completed Step 1 (DB Schema), Step 2 (Entities), and Step 3 (DTOs). Now, please execute **Step 4: All Repositories**.

Generate all Spring Data JPA repository interfaces in `com.att.tdp.issueflow.repository`. Each extends `JpaRepository`. Use derived method names where possible; use `@Query` (JPQL) only when necessary.

1. **UserRepository**: `findByUsername`, `findByUsernameIgnoreCase`, `existsByUsername`, `existsByEmail`.
2. **ProjectRepository**: `findByDeletedAtIsNull`, `findByDeletedAtIsNotNull`, `findByIdAndDeletedAtIsNull`.
3. **TicketRepository**: `findByProjectIdAndDeletedAtIsNull`, `findByProjectIdAndDeletedAtIsNotNull`, `findByIdAndDeletedAtIsNull`. Plus `@Query` to count open tickets per assignee in a project (returns `List<Object[]>` with `[assigneeId, count]`). Plus `@Query` for overdue non-CRITICAL tickets (`dueDate < NOW AND status != DONE AND priority != CRITICAL AND deletedAt IS NULL`). Plus `@Query` for already-CRITICAL overdue tickets where `isOverdue = false`.
4. **CommentRepository**: `findByTicketId`. Plus `@Query` to find comments mentioning a specific user (joining the many-to-many), with `Pageable` returning `Page<Comment>`.
5. **AuditLogRepository**: `findByEntityTypeAndEntityIdOrderByTimestampDesc`.
6. **AttachmentRepository**: `findByTicketId`.
7. **TicketDependencyRepository**: `findByTicketId`, `existsByTicketIdAndBlockedById`.

---

## Step 5 - JWT Security Layer

We have completed Steps 1 through 4 (Schema, Entities, DTOs, Repositories). Now, we must implement **Step 5: JWT Security Layer** before touching the business logic.

Add Spring Security with stateless JWT authentication. Provide pom.xml snippets for `spring-boot-starter-security` and JJWT (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`). Generate these classes in `com.att.tdp.issueflow.security`:

1. **`JwtUtil.java`** - Secret key from `@Value("${jwt.secret}")`. Methods: `generateToken(username)`, `extractUsername(token)`, `validateToken(token, userDetails)`. Expiration: 24 hours.
2. **`JwtAuthFilter.java`** - Extends `OncePerRequestFilter`. Extracts `Authorization: Bearer` header, validates via `JwtUtil`, sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`. Skips if token is in logout blocklist.
3. **`SecurityConfig.java`** - `@Configuration @EnableWebSecurity`. Stateless sessions, CSRF disabled. Public: `POST /api/users`, `POST /api/auth/login`. Everything else requires authentication. Registers `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`. Provides `AuthenticationManager` and `BCryptPasswordEncoder` beans.
4. **`CustomUserDetailsService.java`** - Implements `UserDetailsService`. Loads user by username, maps role to `GrantedAuthority`.

---

## Step 6 - All Services (Business Logic)

### 6.1

Let's implement the foundational logging layer first: `AuditLogService.java`. Single public method: `log(String action, String entityType, Long entityId, User performedBy)`. Sets timestamp to `LocalDateTime.now()`, saves to DB. Handle null `performedBy` for system actions.

### 6.2

Next, let's implement the user management and authentication logic: `UserService.java` and `AuthService.java`.

**UserService** (implements `UserDetailsService`):
- Registration: validate username/email uniqueness (throw `ConflictException` if taken), hash password, save, log `"CREATE"`.
- Also: `getAllUsers()`, `getUserById(id)`, `updateUser(id, request)` (partial update - skip email uniqueness check if unchanged), `deleteUser(id)`, `getUserMentions(userId, page, pageSize)` (paginated).

**AuthService:**
- `login(LoginRequest)`: authenticate via `AuthenticationManager`, return JWT.
- `logout(token)`: add to in-memory blocklist.
- `getMe(username)`: return current user as `UserResponse`.

### 6.3

Next, let's implement `ProjectService.java` to handle project lifecycle:
- `createProject`: validate owner exists, save, log `"CREATE"`.
- `getProjectById`: throw `ResourceNotFoundException` if not found or soft-deleted.
- `getAllActiveProjects`, `getDeletedProjects`.
- `updateProject`: partial update - only apply non-null fields.
- `softDeleteProject`: set `deletedAt = now()`, save, log `"DELETE"`.
- `restoreProject`: clear `deletedAt`, save, log `"RESTORE"`. Throw `IllegalStateException` if not currently deleted.
- `getWorkload(projectId)`: return `List<WorkloadEntry>` sorted ascending by `openTicketCount`.

### 6.4

Next, let's implement the core ticket features: `TicketService.java`, `DependencyService.java`, and `AttachmentService.java`.

**TicketService:**
- `createTicket`: if no `assigneeId` provided, auto-assign to the DEVELOPER with the fewest open tickets in the project (tie-break: lowest user ID). Log `"AUTO_ASSIGN"` with null actor if triggered. Log `"CREATE"`.
- `updateTicket`: partial update. Enforce forward-only transitions (TODO → IN_PROGRESS → IN_REVIEW → DONE). Block DONE transition if there are unresolved blockers. Manual priority change resets `isOverdue = false`.
- `softDeleteTicket`: set `deletedAt = now()`, log `"DELETE"`.
- `restoreTicket`: clear `deletedAt`, log `"RESTORE"`. Throw `IllegalStateException` if not deleted.

**DependencyService:**
- `addDependency`: validate no self-dependency, same project, no duplicates. Log `"CREATE"`.
- `removeDependency`: log `"DELETE"`.
- `getDependencies(ticketId)`: return list of blocker tickets.

**AttachmentService:** store files as `byte[]` linked to a ticket. Log `"UPLOAD_ATTACHMENT"`.

### 6.5

Next, let's implement `CommentService.java` which includes live text parsing for user mentions:
- `addComment`: parse `content` with regex `@(\w+)`. For each match, look up via `findByUsernameIgnoreCase` - add found users to `mentionedUsers`, silently skip unknown usernames. Save, log `"ADD_COMMENT"`.
- `getCommentsForTicket(ticketId)`: return all comments as `CommentResponse`.
- `updateComment`: update content AND re-parse mentions - replace existing `mentionedUsers` with freshly parsed results.
- `deleteComment`: log `"DELETE"`.

### 6.6

Finally, let's implement `CsvService.java` and `EscalationService.java`.

**CsvService (Apache Commons CSV):**
- `exportTicketsToCsv(projectId, writer)`: write all active tickets to CSV (columns: ID, Title, Status, Priority, Type, Assignee).
- `importTicketsFromCsv(inputStream, projectId, currentUser)`: parse row by row with per-row error tolerance. Auto-assign if no assignee. Return `CsvImportResult` (successfulCount, failedCount, errors).

**EscalationService:**
- `@Scheduled(fixedRate = 60_000)` with `@Transactional`.
- Step 1: fetch overdue non-CRITICAL tickets, promote one level (LOW→MEDIUM, MEDIUM→HIGH, HIGH→CRITICAL). Set `isOverdue = true` when reaching CRITICAL. Log `"AUTO_ESCALATE"` with null actor.
- Step 2: fetch already-CRITICAL overdue tickets where `isOverdue = false` - set flag, log.
- Add `@EnableScheduling` to `IssueFlowApplication.java`.

---

## Step 7 - All Controllers & Exception Handling

We are entering the final structural phase of the IssueFlow backend: **Step 7: All Controllers & Exception Handling**.

Generate all REST Controllers in `com.att.tdp.issueflow.controller` and the exception handler in `com.att.tdp.issueflow.exception`.

**General requirements:** `@RestController`, all endpoints prefixed `/api`, constructor injection via `@RequiredArgsConstructor`, `@Valid` on all request bodies, `@AuthenticationPrincipal` to resolve the current user, standard HTTP codes (`201` for creates, `204` for deletes).

**Route ordering:** in `ProjectController` and `TicketController`, static paths (`/deleted`, `/export`) must be declared BEFORE parameterized paths (`/{id}`) to prevent Spring treating literal words as path variable values.

1. **`AuthController`** (`/api/auth`) - `POST /login`, `POST /logout`, `GET /me`.
2. **`UserController`** (`/api/users`) - `POST` (public, registration), `GET`, `GET /{id}`, `PATCH /{id}`, `DELETE /{id}`, `GET /{id}/mentions`.
3. **`ProjectController`** (`/api/projects`) - `POST`, `GET`, `GET /deleted`, `GET /{id}`, `PATCH /{id}`, `DELETE /{id}`, `POST /{id}/restore`, `GET /{id}/workload`.
4. **`TicketController`** (`/api/tickets`) - `POST`, `GET`, `GET /deleted`, `GET /export`, `GET /{id}`, `PATCH /{id}`, `DELETE /{id}`, `POST /{id}/restore`, `GET /{id}/dependencies`, `POST /{id}/dependencies`, `DELETE /{id}/dependencies/{blockedById}`, `POST /import`.
5. **`CommentController`** (`/api`) - CRUD under `/tickets/{ticketId}/comments`. Also `GET /comments/mentions` (paginated, current user's mentions).
6. **`AttachmentController`** (`/api/tickets/{ticketId}/attachments`) - `POST` (upload `MultipartFile`), `GET /{attachmentId}` (download with `Content-Type` and `Content-Disposition` headers).
7. **`AuditLogController`** (`/api/audit-logs`) - `GET` with optional `entityType` and `entityId` query params.
8. **`GlobalExceptionHandler`** (`@RestControllerAdvice`) - structured `ErrorResponse` (timestamp, status, error label, message, field-level details). Map every exception: not-found, conflict (including optimistic lock failures), bad input (service layer + bean validation), access denied, file size exceeded, and a catch-all for unexpected errors. Include per-field messages for validation failures.

---

## Step 8 - CSV Import/Export

We are now implementing the final functional requirements for the IssueFlow backend: **CSV Import/Export**.

Generate or verify `CsvService.java` and the corresponding endpoints in `TicketController`.

**Export:** `GET /api/tickets/export?projectId=X` - set `Content-Type: text/csv` and `Content-Disposition: attachment; filename="tickets.csv"`. Stream all active tickets via Apache Commons CSV.

**Import:** `POST /api/tickets/import?projectId=X` - accepts `multipart/form-data`. Parse row by row with per-row error isolation. Return:
```json
{ "successfulCount": N, "failedCount": M, "errors": ["Row 2: Missing title", "Row 5: Invalid priority"] }
```

---

## Step 9 - Unit Tests & Documentation

We are completing the final phase of the IssueFlow backend project: **Step 9: Unit Tests**.

Write unit tests using JUnit 5 and Mockito. No Spring context - all dependencies mocked with `@ExtendWith(MockitoExtension.class)`.

1. **`UserServiceTest`**: registration success (password encoded, audit logged, plain-text never saved); duplicate username/email → `ConflictException`. `getAllUsers`, `getUserById` (success + 404), `updateUser` (email change, duplicate new email, same email skips uniqueness check), `deleteUser` (success + 404).

2. **`TicketServiceTest`**: auto-assignment picks dev with fewest open tickets; all devs at zero → lowest ID wins; no devs → null assignee; explicit assigneeId skips auto-assign; unknown assigneeId → 404. Soft-delete sets `deletedAt`. `restoreTicket` clears `deletedAt`; throws if not deleted; 404 if not found. `updateTicket`: null fields unchanged; DONE ticket immutable; backward status → throws; DONE with open blockers → throws; manual priority change resets `isOverdue`.

3. **`CommentServiceTest`**: mention parsing extracts multiple `@username` matches, queries `findByUsernameIgnoreCase`, maps to `mentionedUsers`. Unknown mention → skipped silently. No `@` in content → repository never called. Mixed known/unknown → only known attached. Ticket not found → throws, comment not saved. `updateComment` re-parses mentions. `deleteComment` success + 404.

4. **`EscalationServiceTest`**: LOW→MEDIUM (`isOverdue` stays false); MEDIUM→HIGH (`isOverdue` stays false); HIGH→CRITICAL (`isOverdue = true`); already-CRITICAL without flag → flag set, priority unchanged; no overdue tickets → no saves, no logs.

5. **`ProjectServiceTest`**: `createProject` success; owner not found → 404. `getProjectById` success + 404. `updateProject` partial (null name unchanged). `softDeleteProject` sets `deletedAt` + audit; 404 if not found. `restoreProject` clears `deletedAt` + audit; already-active → `IllegalStateException`; 404.

6. **`DependencyServiceTest`**: `addDependency` success; self-dependency → `IllegalArgumentException`; different projects → `IllegalArgumentException`; duplicate → `ConflictException`. `removeDependency` success; not found → `ResourceNotFoundException`. `getDependencies` returns correct blocker list.

Use descriptive test names (e.g., `createTicket_autoAssignment_selectsDevWithFewestOpenTickets`).

---

## Prompt 10 - Refinements & Bug Fixes

I've been testing everything end-to-end and ran into a few things that don't behave the way I expected. Can you go through these and fix them?

### 10.1 - Mentions not working when capitalized

I created a user with the username "alice", then wrote `@Alice` in a comment and the mention wasn't saved. Looks like the mention lookup cares about capitalization - can you make it work regardless of how the username is typed?

Also I noticed that if I go back and edit a comment and change who I mentioned, the mention list from the original comment stays the same. It should update to reflect what's actually in the edited text.

### 10.2 - Escalation scheduler isn't promoting HIGH tickets

I've been watching the escalation run and it seems like HIGH priority tickets never get promoted to CRITICAL. LOW and MEDIUM go up fine, but HIGH ones just stay where they are. Something in the query or the logic looks off.

Also I wanted to write unit tests for the escalation logic but realized it's all written inside the scheduler directly - there's no service class for it. Every other feature has a service, can you move the logic there so I can test it properly?

One more thing - the `isOverdue` flag doesn't seem to stay saved. After the scheduler runs and sets it, something resets it. I need it to actually be stored in the database like a real column.

### 10.3 - Auto-assignment has a few gaps

When a ticket gets auto-assigned, I don't see any entry in the audit log for it. Should there be one? It feels like it should be tracked the same way other actions are.

Also I noticed that when I call the workload endpoint, the order of results seems different every time. Can you make it return them in a consistent order?

And I tested a scenario where all developers had 0 open tickets - the assignment seemed arbitrary. Is there supposed to be a rule for that case?

### 10.4 - Empty string slips through PATCH validation

I sent a PATCH request with `"title": ""` and it saved successfully. That seems like a bug - an empty title shouldn't be valid. Can you add validation to block that?

One catch though - I tried adding `@NotBlank` to the DTO field and it broke partial updates entirely, because it also rejects null and that means you can't omit a field anymore. Is there a way to allow null (field not sent) but still reject empty strings?

---

## Prompt 11 - System-Level Improvements

### 11.1 - BFS Circular Dependency Detection

`DependencyService.addDependency` validates self-reference, same-project, and duplicates - but does NOT detect transitive cycles. A chain like A ← B ← C and then C ← A creates an unresolvable deadlock.

Add `findBlockerIdsByTicketId(Long ticketId)` to `TicketDependencyRepository` (JPQL: `SELECT d.blockedBy.id FROM TicketDependency d WHERE d.ticket.id = :ticketId`). Add a private `wouldCreateCycle(Long ticketId, Long newBlockerId)` BFS method to `DependencyService`: start from `newBlockerId`, follow the existing blocker chain at each step, return true if `ticketId` is reached. Throw `IllegalArgumentException` if a cycle is detected. Add test: `addDependency_wouldCreateCycle_throwsIllegalArgumentException`.

### 11.2 - Cascaded Soft Delete for Projects

`softDeleteProject` sets `deletedAt` on the project but leaves all its tickets active. Similarly, `restoreProject` only restores the project record.

In `softDeleteProject`: after saving the project, fetch all active tickets via `findByProjectIdAndDeletedAtIsNull` and set their `deletedAt` to the same timestamp; `saveAll`. In `restoreProject`: after saving, fetch all deleted tickets via `findByProjectIdAndDeletedAtIsNotNull`, clear their `deletedAt`, `saveAll`. Both are already `@Transactional`. Add tests: `softDeleteProject_cascadesToActiveTickets` and `restoreProject_cascadesToDeletedTickets`.
