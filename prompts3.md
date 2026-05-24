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

Read the full assignment and all project files. I need a clear picture of what has to be built before writing any code - what APIs are required, what's already in the skeleton, and what needs to be implemented from scratch. Give me a 3-day implementation plan ordered by dependencies, and a summary of the main technical decisions I'll need to make along the way.

---

## Step 0.5 - First Vertical Slice

Start with user management - just the entity, repository, basic CRUD service and controller. I want to get a working endpoint before expanding to other features. Don't touch projects, tickets, or auth yet.

---

## Step 1 - Database Schema

Generate the full PostgreSQL schema for IssueFlow. The system needs tables for users, projects, tickets, comments, mentions, ticket dependencies, attachments, and audit logs. Tickets and projects should support soft delete. Make sure the design supports all the features described in the assignment - auto-assignment, escalation, mentions, dependencies.

---

## Step 2 - Entities & Enums

Now generate all the JPA entities and enums based on the schema. Use Lombok throughout. The ticket entity needs an `isOverdue` flag that actually persists to the database - not transient. Add optimistic locking where it makes sense. Handle the circular serialization issues that come up with bidirectional relationships.

---

## Step 3 - DTOs

Generate all the request and response DTOs. Add validation annotations on all request objects. Passwords should never appear in any response. For comments, the response should include the list of mentioned users. PATCH requests need to support partial updates - only fields that are actually sent should be updated.

---

## Step 4 - Repositories

Generate all the Spring Data JPA repositories. Use derived method names where possible. I'll need custom queries for: counting open tickets per developer in a project, finding overdue tickets that aren't CRITICAL yet, finding CRITICAL tickets where the overdue flag hasn't been set, and paginated mentions per user.

---

## Step 5 - JWT Security

Add Spring Security with stateless JWT. Public endpoints are user registration and login - everything else requires a valid token. Logging out should actually invalidate the token on the server side so it can't be reused. Map user roles to Spring Security authorities.

---

## Step 6 - Services

Implement all the business logic. Start with the audit log service since everything else depends on it. Then work through users, auth, projects, tickets, comments, dependencies, attachments, CSV, and the escalation scheduler.

Key rules to enforce:
- Tickets can only move forward through statuses, never backward
- Closing a ticket is blocked if it still has open blockers
- If no assignee is given on ticket creation, auto-assign to the developer with the fewest open tickets
- Comments support `@username` mentions - parse them, look up the users, and attach them to the comment
- The escalation scheduler runs every 60 seconds and bumps overdue tickets one priority level

---

## Step 7 - Controllers & Error Handling

Wire up all the REST controllers and a global exception handler. Make sure static route paths like `/deleted` and `/export` are declared before parameterized ones like `/{id}` to avoid routing conflicts. The error response should include the timestamp, status code, a short label, the message, the request path, and field-level details for validation failures.

---

## Step 8 - CSV Import/Export

Add export and import endpoints for tickets. Export streams a CSV file for a given project. Import reads the file row by row - if a row fails it should be logged and skipped, not abort the whole import. Return a summary with success/failure counts and error messages.

---

## Step 9 - Unit Tests

Write unit tests for all the service classes using JUnit 5 and Mockito - no Spring context. Focus on the edge cases: auto-assignment logic, status transition rules, mention parsing, escalation priority steps, cascade soft delete behavior, and dependency validation. Use descriptive test method names.

---

## Step 10 - Bug Fixes

Found a few issues during testing:

- Mentions are case-sensitive - `@Alice` doesn't match a user registered as `alice`. Also, editing a comment doesn't update the mention list.
- The escalation scheduler never promotes HIGH tickets to CRITICAL. Also, the logic is embedded in the scheduler class itself - move it to a service so it can be unit tested.
- Auto-assignment doesn't create an audit log entry. Also, when all developers have 0 tickets the assignment isn't deterministic.
- Sending an empty string for a field in a PATCH request passes validation even though it shouldn't. Need to block empty strings but still allow null (so omitting a field still works).

---

## Step 11 - Circular Dependencies & Cascade

Two things I realized are missing:

The dependency validation doesn't check for transitive cycles - you can create A blocks B, B blocks C, C blocks A and the system allows it. Add BFS cycle detection before saving a new dependency.

Also, soft-deleting a project doesn't affect its tickets. When a project is deleted all its active tickets should be soft-deleted too. When the project is restored, only tickets that were deleted with it should come back - tickets that were individually deleted before the project should stay deleted.
