package com.skylanka.ticketmanagement.repository;

import com.skylanka.ticketmanagement.entity.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access layer for TicketHistory (immutable snapshots).
 */
@Repository
public interface TicketHistoryRepository extends JpaRepository<TicketHistory, UUID> {

    /** Return all history records for a ticket, oldest first. */
    List<TicketHistory> findByTicketIdOrderByChangedAtAsc(UUID ticketId);
}
