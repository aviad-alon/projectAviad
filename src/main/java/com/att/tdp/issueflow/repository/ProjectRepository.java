package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** Returns all active (non-deleted) projects */
    List<Project> findByDeletedAtIsNull();

    /** Returns all soft-deleted projects */
    List<Project> findByDeletedAtIsNotNull();

    /** Find an active project by ID (excludes soft-deleted) */
    Optional<Project> findByIdAndDeletedAtIsNull(Long id);
}
