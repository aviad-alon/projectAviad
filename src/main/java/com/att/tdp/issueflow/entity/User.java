package com.att.tdp.issueflow.entity;

import com.att.tdp.issueflow.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a system user.
 * Passwords are stored as BCrypt hashes - never in plain text.
 * Username and email must be unique across all users.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Unique login handle - max 50 characters. */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Unique contact email. */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** DEVELOPER or ADMIN - stored as a string in the database. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** BCrypt-hashed password - never returned in API responses. */
    @Column(nullable = false)
    private String password;
}
