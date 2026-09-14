package com.skylanka.ticketmanagement.repository;

import com.skylanka.ticketmanagement.entity.stubs.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Stub repository for Booking entity.
 * Replace with the real Booking repository from the Booking Management module.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
}
