# IssueFlow - AI Prompts Log

## Tools Used

**Claude Code (claude-sonnet-4-6)** - used as the primary coding assistant throughout the project. Responsible for all code generation, implementation, refactoring, bug fixes, and running tests directly in the terminal via the CLI.

**Google Gemini 2.5 Pro & Gemini 2.5 Flash** - used in the early and ongoing phases for understanding the assignment requirements, brainstorming the overall architecture, generating and refining prompts, answering conceptual questions (e.g., Spring Security flow, JPA relationships, JWT internals), and expanding knowledge on relevant topics before diving into implementation.

---

This file documents the prompts used throughout the development of the IssueFlow backend. Prompts are listed in order of use, grouped by development phase.

---

## Prompt 0 - Project Analysis & Implementation Planning

I have a homework assignment for the AT&T TDP 2026 program located in my current working directory. The project is called IssueFlow - a ticket management backend platform.

Read and analyze all project files - including the PDF requirements document, both the issueflow-java/ and issueflow-typescript/ skeleton projects, all READMEs, pom.xml, package.json, source files, and configuration files.

Then do the following:

1. Explain the assignment clearly - what needs to be built, what APIs are required, what special features are needed (soft delete, auto-assignment, auto-escalation, mentions, JWT auth, CSV import/export), and what is already provided vs. what needs to be implemented.

2. Create a 3-day implementation plan ordered by dependencies, where each day focuses on a distinct layer or feature set - allowing me to go deep into each topic before moving on. I chose to implement in Java (Spring Boot).

3. Generate a PDF guide in English covering: a plain-English explanation of the assignment, the full system architecture diagram, package/folder hierarchy, all required API endpoints with descriptions, step-by-step build order, and key technical tips and Spring Boot annotation reference.

---

## Prompt 0.5 - Architecture Strategy Exploration (Vertical Slicing)

You are a Senior Java and Spring Boot Software Architect. We are building the "IssueFlow" backend using the existing skeleton in `issueflow-java/`.

To ensure stability, we will follow a strict "Vertical Slicing" development strategy. We will implement one feature end-to-end (from the database layer up to the REST Controller) before moving on to other entities.

Let's start with the first slice: User Management & Authentication.

Please guide me and generate the required code ONLY for the User entity components, following the package structure specified in our architecture:

1. `entity/User.java`: Create the JPA entity with basic fields (id, username, email, password, role, dates) mapped to PostgreSQL.
2. `repository/UserRepository.java`: Create the Spring Data JPA interface.
3. `dto/`: Create the necessary Request/Response DTOs for User registration (e.g., `CreateUserRequest.java`).
4. `service/UserService.java`: Implement the business logic for user creation (including a placeholder or basic layout for password hashing).
5. `controller/UserController.java`: Create the REST endpoints for registering a user.

Rules for this interaction:
- Do NOT touch other entities like Project, Ticket, or Comments yet.
- Adhere strictly to the existing Spring Boot configuration and properties.
- Do not write the full JWT security filter logic yet; focus purely on the functional User CRUD/Registration flow first.

Please analyze the existing skeleton structure and provide the steps and code to implement this first vertical slice for the User component.

---

## Step 1 - Database Schema

You are a Senior PostgreSQL Database Architect. I am building the "IssueFlow" backend system. We are following a strict Horizontal Slicing development strategy.

Our first milestone is to create the complete database schema.

Please write the full `schema.sql` file containing all 8 required tables. Use PostgreSQL syntax. Here are the exact requirements for the tables based on the system architecture:

1. **`users`**: id, username, email, full_name, role (must be `'DEVELOPER'` or `'ADMIN'`), password.
2. **`projects`**: id, name, description, owner_id (FK to users), deleted_at (nullable; null means active).
3. **`tickets`**: id, title, status (must be `'TODO'`, `'IN_PROGRESS'`, `'IN_REVIEW'`, or `'DONE'`), priority (must be `'LOW'`, `'MEDIUM'`, `'HIGH'`, or `'CRITICAL'`), type, project_id (FK to projects), assignee_id (FK to users, nullable), due_date, deleted_at (nullable), is_overdue (BOOLEAN NOT NULL DEFAULT FALSE).
4. **`comments`**: id, ticket_id (FK to tickets), author_id (FK to users), content, created_at.
5. **`comment_mentions`**: comment_id (FK to comments), user_id (FK to users). This is a join table for @username references.
6. **`ticket_dependencies`**: ticket_id (FK to tickets), blocked_by_id (FK to tickets). Ticket A is blocked by Ticket B.
7. **`attachments`**: id, ticket_id (FK to tickets), filename, content_type, data (store file binary as `bytea`).
8. **`audit_logs`**: id, action (CHECK constraint listing all valid values), entity_type, entity_id, performed_by (FK to users, nullable for system actions), timestamp. This is a read-only log.

Requirements for the SQL script:
- Add appropriate `PRIMARY KEY` and `FOREIGN KEY` constraints.
- Ensure referential integrity (e.g., what happens if a project is deleted?).
- Use `TIMESTAMP` for date/time columns (like `deleted_at` and `timestamp`).

Do NOT generate any Java code at this stage. ONLY output the raw `schema.sql` code.

---

## Step 2 - Entities & Enums

You are a Senior Java and Hibernate/JPA Architect. We are continuing the development of the "IssueFlow" backend using the Horizontal Slicing strategy.

We successfully completed the database schema (Step 1). Now, we are moving to **Step 2: Creating all Enums and JPA Entities** under the packages `com.att.tdp.issueflow.enums` and `com.att.tdp.issueflow.entity`.

Please generate the Java code for all the required Enums and Entities based on the following structural specifications:

### Part 1: Enums (`com.att.tdp.issueflow.enums`)
1. **UserRole**: DEVELOPER, ADMIN
2. **TicketStatus**: TODO, IN_PROGRESS, IN_REVIEW, DONE
3. **TicketPriority**: LOW, MEDIUM, HIGH, CRITICAL
4. **TicketType**: BUG, FEATURE, TECHNICAL

### Part 2: JPA Entities (`com.att.tdp.issueflow.entity`)
Please use Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`) and standard JPA annotations (`@Entity`, `@Table`, `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`, `@Column`). All Enum fields must be annotated with `@Enumerated(EnumType.STRING)`.

Implement the entities with these exact specifications and relationships:

1. **User.java**
   - Fields: `Long id` (PK), `String username` (Unique, non-nullable), `String email` (Unique, non-nullable), `String fullName`, `UserRole role`, `String password` (non-nullable).

2. **Project.java**
   - Fields: `Long id` (PK), `String name` (non-nullable), `String description`, `LocalDateTime deletedAt` (nullable, for soft-delete).
   - Relationship: `@ManyToOne` with User (`owner`) -> Configure with fetch = FetchType.LAZY.

3. **Ticket.java**
   - Fields: `Long id` (PK), `String title` (non-nullable), `TicketStatus status`, `TicketPriority priority`, `TicketType type`, `LocalDateTime dueDate`, `LocalDateTime deletedAt` (nullable), `boolean isOverdue` stored as `@Column(name = "is_overdue", nullable = false)` - this must be a real database column, NOT a `@Transient` computed field.
   - Relationships:
     - `@ManyToOne` with Project (`project`).
     - `@ManyToOne` with User (`assignee`) -> Nullable.
   - Add `@Version` for optimistic locking.

4. **Comment.java**
   - Fields: `Long id` (PK), `String content` (non-nullable), `LocalDateTime createdAt`.
   - Relationships:
     - `@ManyToOne` with Ticket (`ticket`).
     - `@ManyToOne` with User (`author`).
     - `@ManyToMany` with User (`comment_mentions` join table) to track users @mentioned in this comment.
   - Add `@Version` for optimistic locking.

5. **TicketDependency.java**
   - Composite PK via `@IdClass(TicketDependencyId.class)`.
   - Tracks blockers: `ticket` (`@ManyToOne`) blocked by `blockedBy` (`@ManyToOne`).
   - Also create `TicketDependencyId.java` implementing `Serializable` with fields `Long ticket` and `Long blockedBy`.

6. **Attachment.java**
   - Fields: `Long id` (PK), `String filename`, `String contentType`, `byte[] data` (Annotate with `@Lob` / `@Column(columnDefinition = "BYTEA")` for PostgreSQL binary storage).
   - Relationship: `@ManyToOne` with Ticket (`ticket`).

7. **AuditLog.java**
   - Fields: `Long id` (PK), `String action`, `String entityType`, `Long entityId`, `LocalDateTime timestamp`.
   - Relationship: `@ManyToOne` with User (`performedBy`) -> Nullable (system actions use null).

### Rules for generation:
- Do NOT create any DTOs, Repositories, Services, or Controllers yet. Focus 100% on the data layer mappings.
- Include proper Jackson/JSON annotations if needed to prevent infinite recursion loops on bidirectional relationships (e.g., `@JsonIgnoreProperties`).
- Ensure all entity class names and package names perfectly match the structural layout.

Please write clean, production-ready Java 21 files for each of these classes.

---

## Step 3 - DTOs (Request & Response Objects)

You are a Senior Java and Spring Boot Architect. We are continuing the "IssueFlow" backend project following our strict Horizontal Slicing strategy.

We have successfully completed Step 1 (DB Schema) and Step 2 (Entities). Now, please execute **Step 3: All DTOs**.

Generate the Data Transfer Objects (DTOs) in the `com.att.tdp.issueflow.dto` package.

Requirements:
- Use Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`) for clean code.
- Use `jakarta.validation.constraints.*` (`@NotBlank`, `@NotNull`, `@Email`) for input validation on all Request objects.
- Security & Decoupling: Do NOT expose internal JPA Entities inside the DTOs. Whenever a relationship is needed, use the ID (e.g., `Long projectId`) instead of the Entity object.

Please generate the following Java classes based on the API specifications:

1. **User DTOs**:
   - `CreateUserRequest.java`: username (@NotBlank), email (@NotBlank, @Email), fullName (@NotBlank), role (@NotNull), password (@NotBlank).
   - `UpdateUserRequest.java`: email (@Email), fullName, role. (All fields are optional for partial PATCH updates).
   - `UserResponse.java`: id, username, email, fullName, role. (Do NOT include the password).

2. **Auth DTOs**:
   - `LoginRequest.java`: username (@NotBlank), password (@NotBlank).
   - `LoginResponse.java`: token (String).

3. **Project DTOs**:
   - `CreateProjectRequest.java`: name (@NotBlank), description, ownerId (@NotNull).
   - `UpdateProjectRequest.java`: name, description. (Used for PATCH /projects/:id, fields are optional for partial updates).
   - `ProjectResponse.java`: id, name, description, ownerId.
   - `WorkloadEntry.java`: userId, username, openTicketCount.

4. **Ticket DTOs**:
   - `CreateTicketRequest.java`: title (@NotBlank), priority (@NotNull), type (@NotNull), projectId (@NotNull), assigneeId (nullable), dueDate (nullable).
   - `UpdateTicketRequest.java`: title, description, status, priority, assigneeId, dueDate. (All optional for PATCH).
   - `AddDependencyRequest.java`: blockedBy (@NotNull). (Used for POST /tickets/:id/dependencies to link a blocker ticket).
   - `TicketResponse.java`: id, title, status, priority, type, projectId, assigneeId, dueDate, isOverdue (boolean).

5. **Comment DTOs**:
   - `CreateCommentRequest.java`: authorId (@NotNull), content (@NotBlank).
   - `UpdateCommentRequest.java`: content (@NotBlank).
   - `CommentResponse.java`: id, ticketId, authorId, content, createdAt, `List<MentionedUserDto> mentionedUsers`.
   - `MentionedUserDto.java`: id, username, fullName. Include a static `from(User user)` factory method.
   - `MentionsPageResponse.java`: content (List of CommentResponse), pageNumber (int), totalElements (long), totalPages (int).

6. **Special API DTOs**:
   - `CsvImportResult.java`: successfulCount (int), failedCount (int), errors (List<String>).

Rules for this interaction:
- Output ONLY the Java code for these DTO classes.
- Do NOT generate any Repositories, Services, or Controllers at this stage. We must finish the DTO layer first.

---

## Step 4 - Repositories (Data Access Layer)

You are a Senior Java and Spring Boot Architect. We are continuing the "IssueFlow" backend project following our strict Horizontal Slicing strategy.

We have completed Step 1 (DB Schema), Step 2 (Entities), and Step 3 (DTOs). Now, please execute **Step 4: All Repositories**.

Generate the Spring Data JPA repository interfaces in the `com.att.tdp.issueflow.repository` package.

Requirements:
- Each interface must extend `org.springframework.data.jpa.repository.JpaRepository<EntityName, Long>`.
- Use Spring Data JPA naming conventions for automatic query generation where possible.
- Use `@Query` with JPQL only when a query is too complex for method naming.
- Ensure proper imports (`java.util.List`, `java.util.Optional`, `org.springframework.data.domain.Pageable`, etc.).

Please generate the following interfaces with the required custom methods:

1. **UserRepository.java**:
   - `Optional<User> findByUsername(String username);`
   - `Optional<User> findByUsernameIgnoreCase(String username);`
   - `boolean existsByUsername(String username);`
   - `boolean existsByEmail(String email);`

2. **ProjectRepository.java**:
   - `List<Project> findByDeletedAtIsNull();` (Fetch active projects)
   - `List<Project> findByDeletedAtIsNotNull();` (Fetch soft-deleted projects)
   - `Optional<Project> findByIdAndDeletedAtIsNull(Long id);`

3. **TicketRepository.java**:
   - `List<Ticket> findByProjectIdAndDeletedAtIsNull(Long projectId);` (Active tickets in a project)
   - `List<Ticket> findByProjectIdAndDeletedAtIsNotNull(Long projectId);` (Soft-deleted tickets in a project)
   - `Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);`
   - `@Query` to count open tickets per assignee in a project - returns `List<Object[]>` where each row is `[assigneeId, count]`. (Used for workload calculation and auto-assignment).
   - `@Query` to find overdue tickets for escalation: `dueDate < CURRENT_TIMESTAMP AND status != DONE AND priority != CRITICAL AND deletedAt IS NULL`. (Used by the escalation scheduler - excludes CRITICAL since those are already at max priority).
   - `@Query` to find already-CRITICAL overdue tickets where `isOverdue = false`. (Used for the second step of escalation - flag-only update).

4. **CommentRepository.java**:
   - `List<Comment> findByTicketId(Long ticketId);`
   - `@Query` to find comments where a specific User ID was mentioned (joining the many-to-many relationship). Support pagination by adding a `Pageable` parameter and returning a `Page<Comment>`.

5. **AuditLogRepository.java**:
   - `List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, Long entityId);`

6. **AttachmentRepository.java**:
   - `List<Attachment> findByTicketId(Long ticketId);`

7. **TicketDependencyRepository.java**:
   - `List<TicketDependency> findByTicketId(Long ticketId);`
   - `boolean existsByTicketIdAndBlockedById(Long ticketId, Long blockedById);`

Rules for this interaction:
- Output ONLY the Java interface code for these Repositories.
- Do NOT generate any Services, Controllers, or DTOs. We must finish the data access layer completely before moving forward.

---

## Step 5 - JWT Security Layer

You are a Senior Spring Security Expert. We are continuing the "IssueFlow" backend project following our Horizontal Slicing strategy.

We have completed Steps 1 through 4 (Schema, Entities, DTOs, Repositories). Now, we must implement **Step 5: JWT Security Layer** before touching the business logic.

Please generate the required security configurations and classes.

### Task 1: `pom.xml` Dependencies
Provide the exact XML snippets to add `spring-boot-starter-security` and the JJWT libraries (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) to the pom.xml file.

### Task 2: Implement the Security Classes (in `com.att.tdp.issueflow.security`)
Generate the following classes using Spring Security 3.0+ standards (Spring Boot 3 / Java 21):

1. **`JwtUtil.java`**:
   - Secret key injected via `@Value("${jwt.secret}")`.
   - Methods: `generateToken(String username)`, `extractUsername(String token)`, and `validateToken(String token, UserDetails userDetails)`.
   - Token expiration should be set to 24 hours.

2. **`JwtAuthFilter.java`**:
   - Must extend `OncePerRequestFilter`.
   - Intercept the incoming HTTP request, extract the `Authorization` header, and check if it starts with `"Bearer "`.
   - If a valid token is found, use `JwtUtil` to extract the username, load the user details, and set the `UsernamePasswordAuthenticationToken` in the `SecurityContextHolder`.
   - Skip if the token is found in a logout blocklist.

3. **`SecurityConfig.java`**:
   - Annotate with `@Configuration` and `@EnableWebSecurity`.
   - Define a `SecurityFilterChain` bean.
   - Disable CSRF (as this is a stateless REST API).
   - Set session management to `SessionCreationPolicy.STATELESS`.
   - **Route Rules**:
     - Allow public, unauthenticated access to `POST /api/users` (registration) and `POST /api/auth/login` (login).
     - Require authentication for ANY other request (`anyRequest().authenticated()`).
   - Add the `JwtAuthFilter` to the filter chain *before* `UsernamePasswordAuthenticationFilter`.
   - Provide beans for `AuthenticationManager` and `PasswordEncoder` (use `BCryptPasswordEncoder`).

4. **`CustomUserDetailsService.java`**:
   - Implements `UserDetailsService`.
   - Loads user by username from `UserRepository`, maps role to `GrantedAuthority`.

Rules for this interaction:
- Do NOT generate any REST Controllers or Business Services yet.
- Focus exclusively on wiring up the JWT infrastructure perfectly.

---

## Step 6 - All Services (Business Logic)

### 6.1

You are a Senior Java Developer. We are starting "Step 6: All Services" for IssueFlow using a step-by-step approach.

Let's implement the foundational logging layer first: `AuditLogService.java` in the `com.att.tdp.issueflow.service` package.

Requirements:
- Create an `AuditLogService` class annotated with `@Service`.
- Inject `AuditLogRepository`.
- Implement a single public method: `public void log(String action, String entityType, Long entityId, User performedBy)`.
- This method should create a new `AuditLog` entity, set the timestamp to `LocalDateTime.now()`, and save it to the database.
- Ensure the method handles cases where `performedBy` might be null (e.g., system actions like auto-escalation or auto-assignment).

Output ONLY the complete implementation for `AuditLogService.java`. Do not create any other services yet.

### 6.2

Next, let's implement the user management and authentication logic: `UserService.java` and `AuthService.java`.

Requirements:

1. `UserService.java`:
   - Must implement Spring Security's `UserDetailsService` interface and override `loadUserByUsername(String username)`.
   - Implement user registration: Accept `CreateUserRequest`, check if the username/email already exists (throw `ConflictException` if they do), hash the password using `PasswordEncoder`, save the new `User` entity, and trigger `AuditLogService.log` with the action `"CREATE"`.
   - Also implement: `getAllUsers()`, `getUserById(Long id)`, `updateUser(Long id, UpdateUserRequest)` (partial update - skip email uniqueness check if email is unchanged), `deleteUser(Long id)`, `getUserMentions(Long userId, int page, int pageSize)` (paginated).

2. `AuthService.java`:
   - Implement `login(LoginRequest)`: Use `AuthenticationManager` to authenticate the user. If successful, generate a JWT token using `JwtUtil` and return a `LoginResponse` containing the token.
   - Implement `logout(String token)`: add token to an in-memory blocklist.
   - Implement `getMe(String username)`: return current user as `UserResponse`.

Please generate both complete service classes. Ensure proper integration with `AuditLogService`.

### 6.3

Next, let's implement `ProjectService.java` to handle project lifecycle.

Requirements:
- `createProject(CreateProjectRequest request, User currentUser)`: Validate that the owner exists via `UserService`, create the project, save it, and log the action `"CREATE"` in `AuditLogService`.
- `getProjectById(Long id)`: Fetch a project. Throw `ResourceNotFoundException` if it doesn't exist or is soft-deleted.
- `getAllActiveProjects()`: Fetch all projects where `deletedAt` is NULL.
- `getDeletedProjects()`: Fetch all projects where `deletedAt` is NOT NULL.
- `updateProject(Long id, UpdateProjectRequest request, User currentUser)`: Partial update - only apply non-null fields from the request.
- `softDeleteProject(Long id, User currentUser)`: Implement **Soft Delete**. Do NOT delete the project from the database. Instead, set `deletedAt = LocalDateTime.now()` on the entity, save it, and log `"DELETE"` in `AuditLogService`.
- `restoreProject(Long id, User currentUser)`: Clear `deletedAt`, save, log `"RESTORE"`. Throw `IllegalStateException` if the project is not currently deleted.
- `getWorkload(Long projectId)`: Return `List<WorkloadEntry>` sorted ascending by `openTicketCount`.

Please generate the complete `ProjectService.java` class.

### 6.4

Next, let's implement the core ticket features: `TicketService.java`, `DependencyService.java`, and `AttachmentService.java`.

Requirements:

1. `TicketService.java`:
   - `createTicket(CreateTicketRequest request, User currentUser)`:
     * **Auto-Assignment Logic**: If `assigneeId` is NOT provided in the request, look up all Users with the role `DEVELOPER`. Find the developer who has the minimum count of open tickets in the project. Assign the ticket to them automatically. Tie-break by lowest user ID (oldest registrant). After saving, log `"AUTO_ASSIGN"` with null actor if auto-assignment fired.
     * Save the ticket and log `"CREATE"` in `AuditLogService`.
   - `updateTicket(Long id, UpdateTicketRequest request, User currentUser)`: Partial update. Enforce forward-only status transitions (TODO → IN_PROGRESS → IN_REVIEW → DONE). Block the transition to DONE if there are any unresolved (non-DONE) blocker tickets. When priority is manually changed, reset `isOverdue = false`.
   - `softDeleteTicket(Long id, User currentUser)`: Set `deletedAt = LocalDateTime.now()`, save, log `"DELETE"`.
   - `restoreTicket(Long id, User currentUser)`: Clear `deletedAt`, save, log `"RESTORE"`. Throw `IllegalStateException` if the ticket is not currently deleted.

2. `DependencyService.java`:
   - `addDependency(Long ticketId, AddDependencyRequest request, User currentUser)`: Validate: a ticket cannot block itself; both tickets must belong to the same project; no duplicate dependencies. Log `"CREATE"`.
   - `removeDependency(Long ticketId, Long blockedById, User currentUser)`: Log `"DELETE"`.
   - `getDependencies(Long ticketId)`: Return list of blocker tickets.

3. `AttachmentService.java`:
   - Implement logic to store files as `byte[]` binary data connected to a ticket, and log `"UPLOAD_ATTACHMENT"`.

Please generate these three complete service classes.

### 6.5

Next, let's implement `CommentService.java` which includes live text parsing for user mentions.

Requirements:
- `addComment(Long ticketId, CreateCommentRequest request, User author)`:
  * Extract mentions: Before saving the comment text, parse the `content` string using the Regular Expression: `@(\w+)`.
  * For each extracted username, search the database using `findByUsernameIgnoreCase` (case-insensitive). If a valid user is found, add them to the comment's `mentionedUsers` collection (mapping to the `comment_mentions` table). If not found, skip silently - do NOT throw an exception.
  * Save the comment and log `"ADD_COMMENT"` in `AuditLogService`.
- `getCommentsForTicket(Long ticketId)`: Return all comments for a ticket mapped to `CommentResponse`.
- `updateComment(Long commentId, UpdateCommentRequest request, User currentUser)`: Update content AND re-parse mentions from the new content - call `parseMentions(request.getContent())` and update `mentionedUsers` before saving.
- `deleteComment(Long commentId, User currentUser)`: Delete and log `"DELETE"`.

Please generate the complete `CommentService.java` class with the Regex matching logic.

### 6.6

Finally, let's implement `CsvService.java` and `EscalationService.java`.

**`CsvService.java`** using Apache Commons CSV libraries:
- `exportTicketsToCsv(Long projectId, Writer writer)`: Fetch all active tickets belonging to the specified project and stream them out in a clean CSV format (Columns: ID, Title, Status, Priority, Type, Assignee).
- `importTicketsFromCsv(InputStream inputStream, Long projectId, User currentUser)`:
  * Parse the uploaded CSV file row by row.
  * For each valid row, construct a ticket entity linked to the project.
  * If a row is missing an assignee, trigger the same **Auto-Assignment** logic built into TicketService.
  * Track statistics: Return a `CsvImportResult` DTO containing `successfulCount`, `failedCount`, and `errors` (list of per-row error messages).

**`EscalationService.java`**:
- `@Service` class with a method annotated with `@Scheduled(fixedRate = 60_000)` and `@Transactional`.
- **Logic - two steps**:
  * Step 1: Find overdue non-CRITICAL tickets (`findOverdueTicketsForEscalation`). For each, promote priority one level: LOW→MEDIUM, MEDIUM→HIGH, HIGH→CRITICAL. When a ticket reaches CRITICAL, also set `isOverdue = true`. Save and log `"AUTO_ESCALATE"` with null actor.
  * Step 2: Find already-CRITICAL overdue tickets where `isOverdue = false` (`findOverdueCriticalWithoutFlag`). Set `isOverdue = true`, save, log `"AUTO_ESCALATE"` with null actor.
- Add `@EnableScheduling` to `IssueFlowApplication.java`.

Please generate both complete service classes.

---

## Step 7 - All Controllers & Exception Handling

You are a Senior Java and Spring Boot Web Architect. We are entering the final structural phase of the "IssueFlow" backend: **Step 7: All Controllers & Exception Handling**.

Please generate the REST Controllers in the `com.att.tdp.issueflow.controller` package and the Exception Handler in the `com.att.tdp.issueflow.exception` package.

### General Controller Requirements:
- Use `@RestController`. All endpoints must be prefixed with `/api`.
- Inject Services using constructor injection (Lombok's `@RequiredArgsConstructor` is allowed).
- Enforce input validation using `@Valid` on all incoming Request DTOs.
- Use `@AuthenticationPrincipal` to extract the currently authenticated user, then resolve to the `User` entity via `UserService`.
- Return appropriate HTTP Status codes: `201 Created` for creations, `200 OK` for successful fetches/updates, `204 No Content` for successful deletes.

### Strict Route Ordering Rule:
In `ProjectController` and `TicketController`, you MUST declare static/explicit sub-paths (like `/deleted`, `/export`) **BEFORE** parameterized wildcard paths (like `/{id}`). This prevents Spring from evaluating words like "deleted" as a path parameter ID.

Please generate the following files:

1. **`AuthController.java`** (`/api/auth`)
   - `POST /login`: Accepts `LoginRequest`, calls `AuthService.login()`, returns `LoginResponse`.
   - `POST /logout`: Invalidates the current token via `AuthService.logout()`.
   - `GET /me`: Secured route. Returns current logged-in user as `UserResponse`.

2. **`UserController.java`** (`/api/users`)
   - `POST`: Public endpoint for registration. Accepts `CreateUserRequest`, returns `UserResponse` (`201 Created`).
   - `GET`, `GET /{id}`, `PATCH /{id}`, `DELETE /{id}`, `GET /{id}/mentions`

3. **`ProjectController.java`** (`/api/projects`)
   - `POST`: Create a project.
   - `GET`: Get all active projects.
   - `GET /deleted`: Get all soft-deleted projects (**Must be defined before /{id}**).
   - `GET /{id}`: Get project by ID.
   - `PATCH /{id}`: Partially update a project.
   - `DELETE /{id}`: Soft delete project (`204 No Content`).
   - `POST /{id}/restore`: Restore a soft-deleted project.
   - `GET /{id}/workload`: Get developer workload statistics for the project.

4. **`TicketController.java`** (`/api/tickets`)
   - `POST`: Create a ticket.
   - `GET`: Get active tickets (filter by `projectId` via query param).
   - `GET /deleted`: Get all soft-deleted tickets (**Must be defined before /{id}**).
   - `GET /export`: CSV export (**Must be defined before /{id}**).
   - `GET /{id}`: Get ticket by ID.
   - `PATCH /{id}`: Update ticket fields.
   - `DELETE /{id}`: Soft delete a ticket (`204 No Content`).
   - `POST /{id}/restore`: Restore a soft-deleted ticket.
   - `GET /{id}/dependencies`, `POST /{id}/dependencies`, `DELETE /{id}/dependencies/{blockedById}`
   - `POST /import`: CSV import.

5. **`CommentController.java`** (`/api`)
   - Ticket-scoped CRUD under `/tickets/{ticketId}/comments` (`POST`, `GET`, `PATCH /{commentId}`, `DELETE /{commentId}`).
   - `GET /comments/mentions`: Paginated feed of comments where the current logged-in user is @mentioned (`MentionsPageResponse`).

6. **`AttachmentController.java`** (`/api/tickets/{ticketId}/attachments`)
   - `POST`: Upload a file as `MultipartFile`, store it as binary data.
   - `GET /{attachmentId}`: Download an attachment. Return proper response headers (`Content-Type`, `Content-Disposition`) along with the raw `byte[]` payload.

7. **`AuditLogController.java`** (`/api/audit-logs`)
   - `GET`: Fetch audit logs. Allow optional filtering via query parameters (`entityType`, `entityId`).

8. **`exception/GlobalExceptionHandler.java`**
   - Annotate with `@RestControllerAdvice`.
   - Create a clean `ErrorResponse` DTO structure inside this file (contains timestamp, status code, error, message, and details list).
   - Handle the following exceptions:

   | Exception | HTTP |
   |---|---|
   | `ResourceNotFoundException` | 404 |
   | `ConflictException` | 409 |
   | `ObjectOptimisticLockingFailureException` | 409 |
   | `IllegalArgumentException` / `IllegalStateException` | 400 |
   | `AccessDeniedException` | 403 |
   | `MethodArgumentNotValidException` | 400 + per-field messages in details list |
   | `MaxUploadSizeExceededException` | 413 |
   | `Exception` (catch-all) | 500 |

Output ONLY clean, production-ready Java 21 code for these controllers and handler. Do not add mock services.

---

## Step 8 - CSV Import/Export

You are a Senior Spring Boot Developer. We are now implementing the final functional requirements for the "IssueFlow" backend: **CSV Import/Export**.

Please generate or verify `CsvService.java` and ensure `TicketController` has these exact endpoints:

**1. CSV Export:**
- Endpoint: `GET /api/tickets/export?projectId=X`
- Behavior: Must set the HTTP response header `Content-Type: text/csv` and `Content-Disposition: attachment; filename="tickets.csv"`.
- Logic: Use `Apache Commons CSV` to query all active tickets for the given `projectId` and write them row by row to the `HttpServletResponse` output stream.

**2. CSV Import:**
- Endpoint: `POST /api/tickets/import?projectId=X`
- Consumes: `multipart/form-data` (receives a `MultipartFile`).
- Logic: Parse the CSV. For each valid row, create a Ticket associated with the `projectId`. If a row is invalid (missing required fields, etc.), catch the exception for that specific row and continue processing the rest.
- Return DTO: Return a JSON response representing the `CsvImportResult` DTO, which must look like:
  ```json
  { "successfulCount": N, "failedCount": M, "errors": ["Row 2: Missing title", "Row 5: Invalid priority..."] }
  ```

Output ONLY the production-ready Java code for the CSV Service methods and the Controller endpoints. Ensure robust error handling for the CSV parser.

---

## Step 9 - Unit Tests & Documentation

You are a Senior QA Automation Engineer and Java Testing Expert. We are completing the final phase of the "IssueFlow" backend project: **Step 9: Unit Tests**.

We need to write comprehensive unit tests using JUnit 5 and Mockito for our core business logic. No Spring context or H2 database is needed - all dependencies are mocked with `@ExtendWith(MockitoExtension.class)`.

Please implement unit test classes for the following services:

1. **`UserServiceTest.java`**:
   - Test successful user registration (verify password encoding via `PasswordEncoder` and audit log triggering).
   - Test registration failure when username or email already exists (asserting that `ConflictException` is thrown).
   - Verify the plain-text password never reaches the repository.
   - Test `getAllUsers`, `getUserById` (success + 404), `updateUser` (email change, duplicate new email, same email skips uniqueness check), `deleteUser` (success + 404).

2. **`TicketServiceTest.java`**:
   - Test **Auto-Assignment Logic**: mock developers with different open ticket counts → ticket goes to the one with minimum. All devs at zero → lowest user ID wins (tie-break). No developers in system → assignee null. Explicit `assigneeId` → skip auto-assign logic. Explicit `assigneeId` not found → 404.
   - Test soft-delete to ensure `deletedAt` is set correctly instead of a physical delete.
   - Test `restoreTicket` - clears `deletedAt`; throws `IllegalStateException` if not deleted; 404 if not found.
   - Test `updateTicket` - partial update (null fields unchanged), DONE ticket immutable, backward status transition throws, DONE with open blockers throws, manual priority change resets `isOverdue`.

3. **`CommentServiceTest.java`**:
   - Test **Mention Parsing Logic**: mock comment content with multiple `@username` mentions (e.g., `"Hey @alice and @bob, please review"`). Verify the Regex correctly extracts both, queries the database via `findByUsernameIgnoreCase`, and maps them into the comment's mention collection.
   - Unknown mention → silently skipped, no exception.
   - No `@` in content → `userRepository` never called.
   - Mixed known/unknown → only known users attached.
   - Ticket not found → throws, comment not saved. Audit log verified.
   - `updateComment` re-parses mentions. `deleteComment` success + 404.

4. **`EscalationServiceTest.java`**:
   - LOW → MEDIUM (isOverdue stays false).
   - MEDIUM → HIGH (isOverdue stays false).
   - HIGH → CRITICAL (isOverdue set to true).
   - Already CRITICAL without flag → flag set, priority unchanged.
   - No overdue tickets → no saves, no logs.

5. **`ProjectServiceTest.java`**:
   - `createProject`: success; owner not found → 404.
   - `getProjectById`: success; 404.
   - `updateProject`: partial update (null name left unchanged).
   - `softDeleteProject`: sets `deletedAt` + audit; 404 if not found.
   - `restoreProject`: clears `deletedAt` + audit; already-active → `IllegalStateException`; 404.

6. **`DependencyServiceTest.java`**:
   - `addDependency`: success; self-dependency → `IllegalArgumentException`; different projects → `IllegalArgumentException`; duplicate → `ConflictException`.
   - `removeDependency`: success; dependency not found → `ResourceNotFoundException`.
   - `getDependencies`: returns correct blocker list.

Rules for this interaction:
- Use `@ExtendWith(MockitoExtension.class)` for all test classes - no Spring context needed.
- Write clean, self-documenting test case names (e.g., `createTicket_autoAssignment_selectsDevWithFewestOpenTickets`).

Output ONLY the full Java test code.

---

## Prompt 10 - Refinements & Bug Fixes

Follow-up prompts used to correct specific issues discovered during development or testing.

### 10.1 - @Mention: Case-Insensitive Resolution + Re-evaluation on Edit

`CommentService.parseMentions()` currently uses `findByUsername()` (case-sensitive). Two fixes needed:
1. Switch to `findByUsernameIgnoreCase()` so `@Alice` and `@alice` both resolve to the same user.
2. `updateComment()` only updates the content text but doesn't re-evaluate mentions. After setting the new content, also call `parseMentions(request.getContent())` and update `comment.setMentionedUsers(...)` before saving.

Update the corresponding test stubs to match the new method name.

### 10.2 - Auto-Escalation: Three Fixes

Three issues found in the escalation logic:

1. `findOverdueTicketsForEscalation()` has `AND t.priority != HIGH` - this blocks the HIGH→CRITICAL promotion. Remove it. The only exclusion should be `AND t.priority != CRITICAL`.
2. The escalation logic lives in a scheduler class. Move it to a proper `EscalationService` with `@Scheduled` and `@Transactional`.
3. `Ticket.isOverdue` is currently a `@Transient` computed method. It needs to be a real stored `@Column` so the service can explicitly set and clear the flag. Remove the computed method and add `@Column(name = "is_overdue", nullable = false) private boolean isOverdue;` instead. Also add the column to `schema.sql`: `is_overdue BOOLEAN NOT NULL DEFAULT FALSE`.

Add a second query `findOverdueCriticalWithoutFlag()` for already-CRITICAL overdue tickets where `isOverdue = false`.

### 10.3 - Auto-Assignment: Audit Log, Sorting & Tie-Breaking

Three gaps in auto-assignment:

1. When auto-assignment fires, log it as action `"AUTO_ASSIGN"` with `null` actor. Add this to `createTicket()` after the ticket is saved:
   ```java
   if (request.getAssigneeId() == null && assignee != null) {
       auditLogService.log("AUTO_ASSIGN", "TICKET", saved.getId(), null);
   }
   ```
2. In `resolveAssignee()`, add tie-breaking so that when two developers have equal ticket counts, the one with the lower user ID (oldest registrant) wins:
   ```java
   .min(Comparator.comparingLong(dev -> ticketCountMap.getOrDefault(dev.getId(), 0L))
       .thenComparingLong(User::getId))
   ```
3. `ProjectService.getWorkload()` returns an unsorted list. Add `.sorted(Comparator.comparingLong(WorkloadEntry::getOpenTicketCount))` before collect.

### 10.4 - PATCH Validation: Prevent Empty String Updates

`UpdateProjectRequest` and `UpdateTicketRequest` have no constraints. Sending `{"name": ""}` silently sets the name to an empty string.

Use `@Size(min = 1, message = "...")` (not `@NotBlank`) on the `name` field in `UpdateProjectRequest` and `title` in `UpdateTicketRequest`. The reason for `@Size` over `@NotBlank` is that `@Size` treats `null` as valid - so omitting the field in a PATCH request still passes validation, preserving partial-update semantics. `@NotBlank` would incorrectly reject absent fields.

---

## Prompt 11 - Swagger / OpenAPI Interactive Documentation

Add interactive API documentation to the project using springdoc-openapi.

1. Add the dependency `springdoc-openapi-starter-webmvc-ui:2.8.3` to `pom.xml`.
2. Create `OpenApiConfig.java` in a new `config/` package. Configure the OpenAPI bean with: title "IssueFlow API", description with auth instructions (create user, login, authorize), version "1.0.0", contact name "Aviad Alon". Apply a global `bearerAuth` JWT security scheme so every secured endpoint shows the lock icon in Swagger UI.
3. Update `SecurityConfig` to permit unauthenticated access to `/swagger-ui.html`, `/swagger-ui/**`, and `/v3/api-docs/**`.
4. Add springdoc configuration to `application.yaml`: set `swagger-ui.path`, `try-it-out-enabled: true`, and `api-docs.path`. Change `sql.init.mode` from `always` to `never` (Hibernate `ddl-auto: update` manages the schema; `schema.sql` is documentation-only, and Spring Boot 3 throws an error on comment-only SQL init scripts).

Swagger UI accessible at `http://localhost:8080/swagger-ui/index.html`.

---

## Prompt 12 - System-Level Improvements

Two architectural improvements identified by comparing the project against a peer implementation:

### 12.1 - BFS Circular Dependency Detection

`DependencyService.addDependency` validates self-reference, same-project, and duplicates - but does NOT detect transitive cycles. Adding A blocked-by B and B blocked-by C and then C blocked-by A would create an unresolvable deadlock graph.

Fix: add `findBlockerIdsByTicketId(@Param("ticketId") Long ticketId)` to `TicketDependencyRepository` (JPQL: `SELECT d.blockedBy.id FROM TicketDependency d WHERE d.ticket.id = :ticketId`). Add private `wouldCreateCycle(Long ticketId, Long newBlockerId)` to `DependencyService`: BFS starting from `newBlockerId`, following the existing blocker chain at each step. If `ticketId` is reached, the proposed edge would close a loop - throw `IllegalArgumentException`. Add test: `addDependency_wouldCreateCycle_throwsIllegalArgumentException`.

### 12.2 - Cascaded Soft Delete for Projects

`ProjectService.softDeleteProject` sets `deletedAt` on the project but leaves all its tickets active. Similarly, `restoreProject` only restores the project record.

Fix: in `softDeleteProject`, after saving the project, fetch all active tickets via `findByProjectIdAndDeletedAtIsNull` and bulk-set their `deletedAt` to the same timestamp; call `saveAll`. In `restoreProject`, after saving the project, fetch all deleted tickets via `findByProjectIdAndDeletedAtIsNotNull` and clear their `deletedAt`; call `saveAll`. Both methods are already `@Transactional`. Add tests: `softDeleteProject_cascadesToActiveTickets` and `restoreProject_cascadesToDeletedTickets`.
