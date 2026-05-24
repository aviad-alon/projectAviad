# IssueFlow - Setup, Build & Run Guide

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21+ | [Download](https://adoptium.net/) |
| Maven | 3.9+ | Included via Maven Wrapper (`./mvnw`) |
| Docker | Latest | For running PostgreSQL |
| Docker Compose | v2+ | Bundled with Docker Desktop |

---

## 1. Clone the Repository

```bash
git clone https://github.com/aviad-alon/IssueFlow.git
cd IssueFlow
```

---

## 2. Start the Database

The project includes a `compose.yml` that spins up a PostgreSQL instance.

```bash
docker compose up -d
```

This starts PostgreSQL on **port 5433** with the following defaults:

| Property | Value |
|----------|-------|
| Host | `localhost` |
| Port | `5433` |
| Database | `issueflow` |
| Username | `issueflow` |
| Password | `issueflow` |

Verify the container is running:

```bash
docker compose ps
```

To stop the database:

```bash
docker compose down
```

---

## 3. Build the Project

```bash
./mvnw clean package -DskipTests
```

This compiles the source code and produces a runnable JAR under `target/`.

---

## 4. Run the Application

**Option A - Maven (recommended for development):**

```bash
./mvnw spring-boot:run
```

**Option B - JAR:**

```bash
java -jar target/issueflow-*.jar
```

The server starts on **http://localhost:8080**.

---

## 5. Explore the API

Once the application is running, open the interactive API documentation in your browser:

```
http://localhost:8080/swagger-ui/index.html
```

**How to authenticate:**

1. `POST /api/users` - create a user account
2. `POST /api/auth/login` - obtain a JWT access token
3. Click **Authorize** at the top of the Swagger UI and paste the token
4. All secured endpoints are now accessible directly from the browser

The raw OpenAPI spec is available at:

```
http://localhost:8080/v3/api-docs
```

---

## 6. Run the Tests

```bash
./mvnw test
```

The test suite uses an in-memory **H2** database - no running PostgreSQL instance is required.

Expected output:

```
Tests run: 65, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

To run a specific test class:

```bash
./mvnw test -Dtest=TicketServiceTest
./mvnw test -Dtest=ProjectServiceTest
./mvnw test -Dtest=DependencyServiceTest
```

---

## 7. Configuration

All configuration is in `src/main/resources/application.yaml`.

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/issueflow` | Database URL |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema management |
| `jwt.secret` | see yaml | JWT signing key (min 32 chars) |

---

## 8. Project Structure

```
issueflow-java/
- src/
  - main/java/com/att/tdp/issueflow/
    - controller/      REST controllers
    - service/         Business logic
    - entity/          JPA entities
    - enums/           Status, priority, type, role enums
    - dto/             Request and response DTOs
    - repository/      Spring Data JPA repositories
    - security/        JWT authentication and filter chain
    - exception/       Custom exceptions and global error handler
    - config/          OpenAPI configuration
    - scheduler/       Auto-escalation scheduler
  - test/              Unit tests (Mockito, no Spring context)
  - resources/
    - application.yaml Application configuration
    - schema.sql       Database schema reference (DDL)
- compose.yml          Docker Compose for local PostgreSQL
- pom.xml              Maven build descriptor
```
