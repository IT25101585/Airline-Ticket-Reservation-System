package com.skylanka.air.booking.repository;

import com.skylanka.air.booking.entity.Passenger;
import com.skylanka.air.shared.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {
    List<Passenger> findByBookingIdOrderById(Long bookingId);

    @Query("""
        select p from Passenger p
        where p.booking.flight.id = :flightId
        and p.booking.status <> :excludedStatus
        order by p.seat.seatNumber
        """)
    List<Passenger> findManifestByFlightId(
            @Param("flightId") Long flightId,
            @Param("excludedStatus") BookingStatus excludedStatus
    );
}
