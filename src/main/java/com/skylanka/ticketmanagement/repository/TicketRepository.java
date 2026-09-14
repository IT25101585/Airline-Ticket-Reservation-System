package com.skylanka.ticketmanagement.repository;

import com.skylanka.ticketmanagement.entity.Ticket;
import com.skylanka.ticketmanagement.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for Ticket entities.
 * Uses pessimistic locking where required to prevent race conditions.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /** Find ticket by unique ticket number. */
    Optional<Ticket> findByTicketNumber(String ticketNumber);

    /** Find ticket by QR token — used during validation. */
    Optional<Ticket> findByQrCodeToken(String qrCodeToken);

    /**
     * Find the currently ACTIVE ticket for a given booking.
     * Used to detect duplicates before generating a new ticket.
     */
    @Query("SELECT t FROM Ticket t WHERE t.booking.id = :bookingId AND t.ticketStatus = :status")
    Optional<Ticket> findByBookingIdAndStatus(
            @Param("bookingId") UUID bookingId,
            @Param("status") TicketStatus status);

    /**
     * Find all tickets linked to a booking (full history including REISSUED/VOID).
     */
    @Query("SELECT t FROM Ticket t WHERE t.booking.id = :bookingId ORDER BY t.ticketVersion DESC")
    List<Ticket> findAllByBookingIdOrderByVersionDesc(@Param("bookingId") UUID bookingId);

    /**
     * Find paginated tickets owned by a specific user (CUSTOMER's "my-tickets").
     * Supports optional filtering by status.
     */
    @Query("SELECT t FROM Ticket t WHERE t.user.id = :userId " +
           "AND (:status IS NULL OR t.ticketStatus = :status) " +
           "ORDER BY t.issuedAt DESC")
    Page<Ticket> findByUserId(
            @Param("userId") UUID userId,
            @Param("status") TicketStatus status,
            Pageable pageable);

    /**
     * Pessimistic write lock — used when generating or reissuing to prevent duplicates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.booking.id = :bookingId AND t.ticketStatus = 'ACTIVE'")
    Optional<Ticket> findActiveByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

    /** Check whether a ticket number already exists (for uniqueness guarantee). */
    boolean existsByTicketNumber(String ticketNumber);

    /** Check whether a QR token already exists (prevent duplicate tokens). */
    boolean existsByQrCodeToken(String qrCodeToken);
}
