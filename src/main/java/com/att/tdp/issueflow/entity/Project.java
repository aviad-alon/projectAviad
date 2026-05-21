package com.att.tdp.issueflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a project that groups tickets together.
 * Supports soft deletion - deleted projects set deletedAt instead of being removed from the database.
 */
@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "owner")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Optional longer description - stored as TEXT to allow any length. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** The user responsible for this project. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User owner;

    /** Set when the project is soft-deleted; null means the project is active. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
