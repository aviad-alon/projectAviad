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

*"Please generate a `project_status.md` file containing: current architecture summary, which files have been fully written, important technical decisions made, and the exact list of next steps."*

**Code review:**
Every piece of generated code went through a review pass - both by the agent (asked to self-review for bugs and edge cases) and manually. Corrections were applied iteratively before moving forward.

---

## Step 0 - Project Analysis & Implementation Planning

**Model:** Google Gemini 2.5 Pro

**Assignment Alignment:**
Maps to the initial orientation phase - understanding the full scope of the IssueFlow assignment before writing any code.

**Engineering Intent:**
Avoid building blind. Produce a full picture of what exists, what needs to be built, and in what order dependencies must be resolved before starting implementation.

**Prompt Summary:**
The agent was instructed to read and analyze all project files (PDF requirements, skeleton projects, READMEs, pom.xml, source files, configuration), then explain the assignment, produce a 3-day implementation plan ordered by dependencies, and generate a PDF guide covering the full system architecture, package hierarchy, all required endpoints, and key Spring Boot technical tips.

**Key Design Decisions:**
- Java (Spring Boot) chosen as the implementation language
- Vertical slicing strategy adopted - one feature end-to-end before moving to the next
- 3-day plan with clear daily boundaries to manage scope

**Scope Control:**
- No code was written at this stage
- The plan intentionally deferred security, CSV, and escalation to later steps

**Validation:**
- Plan reviewed against the assignment PDF before implementation began
- Each day's scope was verified to be achievable before committing

---

## Step 0.5 - Architecture Strategy Exploration

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the first vertical slice of the system: User Management and Authentication.

**Engineering Intent:**
Establish the project skeleton and validate the package structure before adding any business logic, so that later steps have a clean base to build on.

**Prompt Summary:**
The agent was instructed to generate only the first slice - `User.java` entity, `UserRepository`, user DTOs, `UserService` with password hashing placeholder, and `UserController` with registration endpoint. Nothing else was to be touched at this stage.

**Key Design Decisions:**
- Package-by-layer structure under `com.att.tdp.issueflow`
- PostgreSQL via `compose.yml` for local development
- JWT security intentionally deferred to Step 5

**Scope Control:**
- Project, Ticket, and Comment layers explicitly excluded
- Full JWT filter not written yet

**Validation:**
- Project compiled successfully with the skeleton in place
- User registration endpoint verified via curl before proceeding

---

## Step 1 - Database Schema

**Model:** Google Gemini 2.5 Pro

**Assignment Alignment:**
Maps to the persistence foundation - all 8 tables required by the assignment.

**Engineering Intent:**
Define the full schema upfront so that all entities, relationships, and constraints are locked in before any JPA code is written. Prevents schema drift during later steps.

**Prompt Summary:**
The agent was instructed to generate `schema.sql` with all 8 tables: `users`, `projects`, `tickets`, `comments`, `comment_mentions`, `ticket_dependencies`, `attachments`, and `audit_logs`. Full PK/FK constraints, referential integrity, and enum check constraints were required. Output was raw SQL only.

**Key Design Decisions:**
- `deleted_at` as nullable timestamp for soft delete (null = active)
- `is_overdue` as a real `BOOLEAN NOT NULL DEFAULT FALSE` column - explicitly not `@Transient`
- `comment_mentions` as a join table for the many-to-many mention relationship
- `performed_by` nullable in audit_logs to support system-initiated actions

**Scope Control:**
- No JPA annotations at this stage
- No application code generated

**Validation:**
- Schema applied to local PostgreSQL via Docker Compose
- All foreign keys verified before proceeding to entity generation

---

## Step 2 - Entities & Enums

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the JPA model layer - all enums and entities needed across the full system.

**Engineering Intent:**
Generate all entities in a single step to avoid incremental mismatches between entity definitions and the schema defined in Step 1.

**Prompt Summary:**
The agent was instructed to generate all enums (`UserRole`, `TicketStatus`, `TicketPriority`, `TicketType`) and all JPA entities (`User`, `Project`, `Ticket`, `Comment`, `TicketDependency` with composite PK, `Attachment`, `AuditLog`) using Lombok and standard JPA annotations. `@Version` was required on `Ticket` and `Comment` for optimistic locking.

**Key Design Decisions:**
- `TicketDependency` modeled as its own entity with `@IdClass` rather than a simple field - enables direct querying, logging, and cycle detection
- `@ManyToMany` for comment mentions via the `comment_mentions` join table
- `@JsonIgnoreProperties` applied where needed to prevent circular serialization
- `FetchType.LAZY` on all `@ManyToOne` associations

**Scope Control:**
- No DTOs, Repositories, Services, or Controllers at this stage

**Validation:**
- Hibernate schema validation run against the Step 1 SQL
- All entities compiled cleanly before proceeding

---

## Step 3 - DTOs

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the API contract layer - all request and response shapes for every endpoint.

**Engineering Intent:**
Keep entities off the wire entirely. All API responses use dedicated DTOs, preventing accidental exposure of internal fields and decoupling the persistence model from the HTTP contract.

**Prompt Summary:**
The agent was instructed to generate all DTOs in `com.att.tdp.issueflow.dto` using Lombok and `jakarta.validation.constraints.*` on all request objects. Covered: User (create, update, response), Auth (login request and response), Project (create, update, response, workload entry), Ticket (create, update, dependency request, response), Comment (create, update, response, mention DTO, mentions page response), and `CsvImportResult`.

**Key Design Decisions:**
- Passwords never appear in any response DTO
- `MentionedUserDto` includes a static `from(User)` factory method
- All PATCH request DTOs use nullable fields to support partial updates
- `MentionsPageResponse` wraps paginated results with metadata

**Scope Control:**
- No service or controller logic generated at this stage

**Validation:**
- All DTOs compiled with Lombok annotation processing verified
- Field-level validation annotations reviewed against assignment constraints

---

## Step 4 - Repositories

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the data access layer - all queries needed by the services defined in Step 6.

**Engineering Intent:**
Use Spring Data derived method names wherever possible, falling back to `@Query` (JPQL) only for queries that cannot be expressed as method names. This keeps the repository layer readable and reduces boilerplate.

**Prompt Summary:**
The agent was instructed to generate all 7 repository interfaces: `UserRepository`, `ProjectRepository`, `TicketRepository` (including JPQL queries for open-ticket counts per assignee, overdue non-CRITICAL tickets, and overdue CRITICAL tickets without the flag), `CommentRepository` (paginated mention query), `AuditLogRepository`, `AttachmentRepository`, and `TicketDependencyRepository`.

**Key Design Decisions:**
- Overdue ticket queries expressed in JPQL filtering on `dueDate < NOW`, `status != DONE`, and `deletedAt IS NULL`
- Mention query uses a join on the many-to-many table with `Pageable` for pagination
- Workload count query returns `List<Object[]>` with `[assigneeId, count]` pairs

**Scope Control:**
- No service logic implemented at this stage
- Native SQL avoided in favor of JPQL throughout

**Validation:**
- Repository methods verified against entity field names
- Queries reviewed for correctness before service implementation

---

## Step 5 - JWT Security Layer

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the authentication and authorization requirements - stateless JWT with role-based access control.

**Engineering Intent:**
Establish security before any business endpoint is exposed. A working filter chain means every subsequent endpoint is protected from the moment it is added.

**Prompt Summary:**
The agent was instructed to add Spring Security with stateless JWT authentication. Four classes were required: `JwtUtil` (token generation and validation with 24-hour expiry), `JwtAuthFilter` (Bearer token extraction, validation, SecurityContext population, and blocklist check), `SecurityConfig` (stateless sessions, CSRF disabled, public routes for registration and login), and `CustomUserDetailsService` (loads user by username, maps role to `GrantedAuthority`).

**Key Design Decisions:**
- In-memory token blocklist for logout invalidation - stolen tokens cannot be reused after logout
- Secret key injected via `@Value("${jwt.secret}")` - never hardcoded
- `POST /api/users` and `POST /api/auth/login` are the only public routes
- `JwtAuthFilter` registered before `UsernamePasswordAuthenticationFilter`

**Scope Control:**
- Refresh tokens not implemented
- Token persistence not required by the assignment

**Validation:**
- Login and protected endpoint access tested via curl before proceeding
- Logout verified to invalidate the token on the server side

---

## Step 6 - Services (Business Logic)

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the full business logic layer across all features.

**Engineering Intent:**
Implement services in dependency order: audit logging first (needed by everything), then users and auth, then projects, then tickets and dependencies, then comments, then CSV and escalation.

**Prompt Summary:**
The agent was instructed to implement all services across 6 sub-steps:
- `AuditLogService` - single `log()` method, null actor supported for system actions
- `UserService` and `AuthService` - registration with uniqueness checks, partial update, pagination for mentions, JWT login, logout with blocklist
- `ProjectService` - full CRUD, soft delete, cascade-aware restore, workload report sorted by open ticket count
- `TicketService`, `DependencyService`, `AttachmentService` - ticket lifecycle with forward-only status transitions, auto-assignment to least-loaded developer (tie-break: lowest ID), blocker validation, file storage
- `CommentService` - `@mention` parsing via regex, case-insensitive lookup, silent skip for unknown usernames, re-parse on update
- `CsvService` and `EscalationService` - row-level error isolation for CSV import, two-phase escalation scheduler with `@Scheduled(fixedRate = 60_000)`

**Key Design Decisions:**
- Auto-assignment logs `"AUTO_ASSIGN"` with null actor when triggered
- Manual priority change in `updateTicket` resets `isOverdue = false`
- `DONE` transition blocked if unresolved blockers exist
- Escalation split into two phases: promote non-CRITICAL tickets, then flag already-CRITICAL ones
- `@EnableScheduling` added to `IssueFlowApplication`

**Scope Control:**
- No controller layer added at this stage
- Attachment storage in-database (`BYTEA`) - no external file system

**Validation:**
- Each service verified against the entity and repository contracts before proceeding
- Business rules tested incrementally via unit tests in Step 9

---

## Step 7 - Controllers & Exception Handling

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the full REST API surface and centralized error handling.

**Engineering Intent:**
Generate all controllers in one step to ensure consistent conventions across the entire API. Centralize all exception mapping in a single `@RestControllerAdvice` so that every error returns the same JSON shape regardless of origin.

**Prompt Summary:**
The agent was instructed to generate all 7 controllers (`AuthController`, `UserController`, `ProjectController`, `TicketController`, `CommentController`, `AttachmentController`, `AuditLogController`) and `GlobalExceptionHandler`. Route ordering was explicitly required - static paths (`/deleted`, `/export`) must be declared before parameterized paths (`/{id}`) to prevent Spring treating literal words as path variables.

**Key Design Decisions:**
- `ErrorResponse` envelope: `timestamp`, `status`, `error`, `message`, `path`, `details`
- `path` field added via `HttpServletRequest.getRequestURI()` to identify the failing endpoint
- Field-level validation errors expanded into the `details` array on `400` responses
- `@AuthenticationPrincipal` used to resolve the current user in write endpoints
- `@PreAuthorize("hasRole('ADMIN')")` on all admin-only endpoints with `@EnableMethodSecurity` in `SecurityConfig`

**Scope Control:**
- Swagger/OpenAPI documentation not included
- No frontend or client SDK generated

**Validation:**
- All endpoints verified via `test-api.sh` against a running local instance
- Route ordering verified by testing `/deleted` and `/export` paths directly

---

## Step 8 - CSV Import/Export

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the bulk data transfer requirement - bidirectional CSV support for tickets.

**Engineering Intent:**
Implement export as a streaming response and import with per-row error isolation, so that a single bad row does not abort the entire batch.

**Prompt Summary:**
The agent was instructed to generate or verify `CsvService` and the corresponding endpoints. Export streams active tickets via Apache Commons CSV with `Content-Disposition: attachment`. Import accepts `multipart/form-data`, parses row by row with isolated error handling, and returns a `CsvImportResult` with success/failure counts and per-row error messages.

**Key Design Decisions:**
- Apache Commons CSV used for both read and write
- Auto-assignment applied during import if no assignee column is present
- Audit record created for each successfully imported ticket
- Failed rows logged in the response but do not roll back successful ones

**Scope Control:**
- Import limited to ticket creation - updates not supported via CSV

**Validation:**
- Export verified to produce valid CSV output
- Import tested with intentionally malformed rows to confirm error isolation

---

## Step 9 - Unit Tests

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to the testing requirement - full unit test coverage for all core service classes.

**Engineering Intent:**
No Spring context. All tests use `@ExtendWith(MockitoExtension.class)` with mocked dependencies. Focus on business logic correctness: edge cases, error paths, and invariants.

**Prompt Summary:**
The agent was instructed to write unit tests for all 6 service classes with descriptive method names. Coverage included: `UserServiceTest` (registration, uniqueness, update, delete), `TicketServiceTest` (auto-assignment logic, soft delete, restore, status transitions, blocker enforcement), `CommentServiceTest` (mention parsing edge cases), `EscalationServiceTest` (all four priority transitions and the no-op case), `ProjectServiceTest` (CRUD, soft delete, cascade restore), `DependencyServiceTest` (add, remove, validation rules, cycle detection).

**Key Design Decisions:**
- H2 in-memory database used for the application context smoke test only
- All service-level tests use Mockito - no database involved
- Test names follow the pattern `methodName_condition_expectedOutcome`

**Scope Control:**
- No controller-layer or integration tests
- No Spring Security test context

**Validation:**
- `./mvnw test` → `Tests run: 69, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS`

---

## Step 10 - Refinements & Bug Fixes

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to correctness and robustness requirements identified during end-to-end testing.

**Engineering Intent:**
Fix behavioral gaps found during manual testing before final submission. Each fix was isolated to avoid regressions.

**Prompt Summary:**
Four issues were reported and fixed: (1) mention lookup was case-sensitive - `findByUsernameIgnoreCase` enforced; mention list not updated on edit - re-parse added to `updateComment`. (2) HIGH tickets not escalating to CRITICAL - query and logic corrected; escalation logic extracted into `EscalationService` to enable unit testing; `isOverdue` field not persisting - confirmed as real DB column. (3) Auto-assignment not logged in audit trail - `AUTO_ASSIGN` log added; workload endpoint order non-deterministic - sort by open ticket count ascending enforced; zero-ticket tie-break made deterministic by lowest user ID. (4) Empty string passing PATCH validation - `@NotBlank` blocks empty strings but rejects null, breaking partial updates; custom validator applied to allow null but reject blank.

**Key Design Decisions:**
- Escalation business logic lives in `EscalationService`, not in the scheduler directly
- Tie-breaking by lowest user ID ensures deterministic auto-assignment
- Null allowed in PATCH fields; empty string rejected via constraint

**Scope Control:**
- Only the four reported issues addressed - no other changes made

**Validation:**
- All 69 unit tests pass after fixes
- Manual re-test of each scenario via `test-api.sh`

---

## Step 11 - System-Level Improvements

**Model:** Claude Code (claude-sonnet-4-6)

**Assignment Alignment:**
Maps to advanced correctness requirements: cycle detection and cascaded soft delete.

**Engineering Intent:**
Address two structural gaps that were not caught during initial implementation: transitive dependency cycles that create unresolvable deadlocks, and orphaned ticket records after project soft delete.

**Prompt Summary:**
Two improvements were implemented: (1) BFS cycle detection in `DependencyService` - `findBlockerIdsByTicketId` added to the repository; a private `wouldCreateCycle` method traverses the existing blocker graph from the proposed new dependency; throws `IllegalArgumentException` if the source ticket is reachable. (2) Cascaded soft delete for projects - `softDeleteProject` now sets the same `deletedAt` timestamp on all active tickets in the project; `restoreProject` clears `deletedAt` on all tickets that were deleted with the same timestamp, leaving independently-deleted tickets untouched.

**Key Design Decisions:**
- BFS chosen over DFS for cycle detection - simpler to reason about and naturally avoids stack overflow on deep chains
- Cascade uses the same timestamp on project and tickets to distinguish project-cascade deletions from individual ticket deletions
- Both operations already `@Transactional` - no additional transaction management needed

**Scope Control:**
- Cycle detection limited to the dependency graph - no other graph traversal added

**Validation:**
- `addDependency_wouldCreateCycle_throwsIllegalArgumentException` test added and passing
- `restoreProject_cascadesToDeletedTickets` and `restoreProject_doesNotRestoreIndividuallyDeletedTickets` tests added and passing
- Full test suite: 69 tests, 0 failures
