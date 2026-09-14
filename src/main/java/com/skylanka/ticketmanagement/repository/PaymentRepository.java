package com.skylanka.ticketmanagement.repository;

import com.skylanka.ticketmanagement.entity.stubs.Payment;
import com.skylanka.ticketmanagement.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Stub repository for Payment entity.
 * Replace with the real Payment repository from the Payment Management module.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Find the most recent PAID payment for a given booking. */
    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId " +
           "AND p.paymentStatus = :status ORDER BY p.createdAt DESC")
    Optional<Payment> findLatestByBookingIdAndStatus(
            @Param("bookingId") UUID bookingId,
            @Param("status") PaymentStatus status);
}
