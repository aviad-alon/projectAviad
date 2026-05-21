package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /** Active (non-deleted) tickets in a project */
    List<Ticket> findByProjectIdAndDeletedAtIsNull(Long projectId);

    /** Soft-deleted tickets in a project */
    List<Ticket> findByProjectIdAndDeletedAtIsNotNull(Long projectId);

    /** Find an active ticket by ID (excludes soft-deleted) */
    Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);

    /** Used for workload calculation and auto-assignment:
     *  returns all open tickets (not DONE) for a given assignee */
    List<Ticket> findByAssigneeIdAndStatusNot(Long assigneeId, TicketStatus status);

    /** Count open tickets per assignee in a specific project (for auto-assignment) */
    @Query("SELECT t.assignee.id, COUNT(t) FROM Ticket t " +
           "WHERE t.project.id = :projectId " +
           "AND t.status != com.att.tdp.issueflow.enums.TicketStatus.DONE " +
           "AND t.deletedAt IS NULL " +
           "AND t.assignee IS NOT NULL " +
           "GROUP BY t.assignee.id")
    List<Object[]> countOpenTicketsPerAssigneeInProject(Long projectId);

    /** Used by the auto-escalation scheduler:
     *  finds all overdue, non-DONE, non-CRITICAL, active tickets (LOW/MEDIUM/HIGH → promote one level) */
    @Query("SELECT t FROM Ticket t " +
           "WHERE t.dueDate < CURRENT_TIMESTAMP " +
           "AND t.status != com.att.tdp.issueflow.enums.TicketStatus.DONE " +
           "AND t.priority != com.att.tdp.issueflow.enums.TicketPriority.CRITICAL " +
           "AND t.deletedAt IS NULL")
    List<Ticket> findOverdueTicketsForEscalation();

    /** Finds overdue CRITICAL tickets that have not yet had the is_overdue flag set.
     *  These are tickets manually set to CRITICAL before the scheduler could flag them. */
    @Query("SELECT t FROM Ticket t " +
           "WHERE t.dueDate < CURRENT_TIMESTAMP " +
           "AND t.status != com.att.tdp.issueflow.enums.TicketStatus.DONE " +
           "AND t.priority = com.att.tdp.issueflow.enums.TicketPriority.CRITICAL " +
           "AND t.isOverdue = false " +
           "AND t.deletedAt IS NULL")
    List<Ticket> findOverdueCriticalWithoutFlag();
}
