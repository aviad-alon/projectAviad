package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** All comments on a specific ticket, ordered by creation time */
    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    /** Paginated list of comments where a specific user was @mentioned.
     *  Joins the comment_mentions many-to-many relationship. */
    @Query("SELECT c FROM Comment c JOIN c.mentionedUsers u WHERE u.id = :userId")
    Page<Comment> findByMentionedUserId(@Param("userId") Long userId, Pageable pageable);
}
