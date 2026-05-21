-- =============================================================
--  IssueFlow – Complete Database Schema
--  PostgreSQL syntax
-- =============================================================

-- ---------------------------------------------------------------
-- 1. USERS
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    full_name  VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL CHECK (role IN ('DEVELOPER', 'ADMIN')),
    password   VARCHAR(255) NOT NULL
);

-- ---------------------------------------------------------------
-- 2. PROJECTS
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS projects (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    deleted_at  TIMESTAMP    DEFAULT NULL
);

-- ---------------------------------------------------------------
-- 3. TICKETS
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tickets (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'TODO'
                    CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),
    priority    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM'
                    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    type        VARCHAR(20)  NOT NULL
                    CHECK (type IN ('BUG', 'FEATURE', 'TECHNICAL')),
    project_id  BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    assignee_id BIGINT       DEFAULT NULL REFERENCES users(id) ON DELETE SET NULL,
    due_date    TIMESTAMP    DEFAULT NULL,
    is_overdue  BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP    DEFAULT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------
-- 4. COMMENTS
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comments (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT    NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id  BIGINT    NOT NULL REFERENCES users(id)   ON DELETE RESTRICT,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version    BIGINT    NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------
-- 5. COMMENT_MENTIONS  (join table: comment ↔ user)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comment_mentions (
    comment_id BIGINT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    PRIMARY KEY (comment_id, user_id)
);

-- ---------------------------------------------------------------
-- 6. TICKET_DEPENDENCIES  (ticket A is blocked by ticket B)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ticket_dependencies (
    ticket_id      BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    blocked_by_id  BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    PRIMARY KEY (ticket_id, blocked_by_id),
    CHECK (ticket_id <> blocked_by_id)   -- a ticket cannot block itself
);

-- ---------------------------------------------------------------
-- 7. ATTACHMENTS
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attachments (
    id           BIGSERIAL    PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    data         BYTEA        NOT NULL
);

-- ---------------------------------------------------------------
-- 8. AUDIT_LOGS  (append-only, no FK cascade deletes)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id           BIGSERIAL    PRIMARY KEY,
    action       VARCHAR(30)  NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'RESTORE', 'ADD_COMMENT', 'AUTO_ESCALATE', 'UPLOAD_ATTACHMENT', 'AUTO_ASSIGN')),
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    BIGINT       NOT NULL,
    performed_by BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    actor        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    timestamp    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------
-- INDEXES  (for common query patterns)
-- ---------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_tickets_project_id   ON tickets(project_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assignee_id  ON tickets(assignee_id);
CREATE INDEX IF NOT EXISTS idx_tickets_deleted_at   ON tickets(deleted_at);
CREATE INDEX IF NOT EXISTS idx_projects_deleted_at  ON projects(deleted_at);
CREATE INDEX IF NOT EXISTS idx_comments_ticket_id   ON comments(ticket_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity    ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_performed ON audit_logs(performed_by);
