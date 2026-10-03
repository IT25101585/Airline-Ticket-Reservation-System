package com.skylanka.air.ticket.repository;

import com.skylanka.air.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByBookingId(Long id);
    Optional<Ticket> findFirstByPassengerIdOrderByIdDesc(Long passengerId);

    /** The newest ticket for a passenger (earlier ones may have been superseded by a reissue). */
    default Optional<Ticket> findByPassengerId(Long passengerId) {
        return findFirstByPassengerIdOrderByIdDesc(passengerId);
    }
    Optional<Ticket> findByTicketNumber(String ticketNumber);
    long countByStatus(com.skylanka.air.shared.entity.TicketStatus status);
}
