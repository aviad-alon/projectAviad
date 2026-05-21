package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.TicketDependencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketDependencyRepository extends JpaRepository<TicketDependency, TicketDependencyId> {

    /** All blocker relationships for a specific ticket
     *  (i.e. every ticket that is blocking this one) */
    @Query("SELECT d FROM TicketDependency d WHERE d.ticket.id = :ticketId")
    List<TicketDependency> findByTicketId(@Param("ticketId") Long ticketId);

    /** Check if a specific blocker relationship already exists */
    @Query("SELECT COUNT(d) > 0 FROM TicketDependency d " +
           "WHERE d.ticket.id = :ticketId AND d.blockedBy.id = :blockedById")
    boolean existsByTicketIdAndBlockedById(
            @Param("ticketId") Long ticketId,
            @Param("blockedById") Long blockedById);
}
