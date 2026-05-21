package com.att.tdp.issueflow.enums;

/**
 * Roles that can be assigned to a user.
 * - DEVELOPER: can be assigned tickets; participates in auto-assignment workload balancing.
 * - ADMIN: has elevated permissions (e.g. restore deleted resources).
 */
public enum UserRole {
    DEVELOPER,
    ADMIN
}
