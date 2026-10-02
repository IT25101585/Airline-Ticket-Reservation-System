package com.skylanka.air.seat.repository;

import com.skylanka.air.seat.entity.Seat;
import com.skylanka.air.shared.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByFlightIdOrderBySeatNumber(Long flightId);

    List<Seat> findByFlightIdAndStatusOrderBySeatNumber(Long flightId, SeatStatus status);

    long countByFlightId(Long flightId);

    long countByFlightIdAndStatusAndHeldBy(Long flightId, SeatStatus status, String heldBy);

    List<Seat> findByFlightIdAndStatusAndHeldBy(Long flightId, SeatStatus status, String heldBy);

    /** Live (non-lapsed) holds owned by one holder, across all flights. */
    long countByStatusAndHeldByAndHeldAtAfter(SeatStatus status, String heldBy, java.time.LocalDateTime cutoff);

    List<Seat> findByStatusAndHeldByIsNotNullAndHeldAtBefore(SeatStatus status, java.time.LocalDateTime cutoff);

    long countByFlightIdAndStatus(
            Long flightId,
            SeatStatus status
    );

    interface FlightLoadFactor {
        Long getFlightId();
        long getBookedCount();
    }

    @Query("select s.flight.id as flightId, count(s) as bookedCount from Seat s where s.status = :status group by s.flight.id")
    List<FlightLoadFactor> countBookedByFlight(@Param("status") SeatStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);
}