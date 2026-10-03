package com.skylanka.air.flight.repository;

import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.shared.entity.FlightStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface FlightRepository
        extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNumber(String n);

    long countByDepartureDate(LocalDate date);

    interface RoutePair {
        String getOrigin();
        String getDestination();
    }

    @Query("""
        select distinct f.origin as origin, f.destination as destination
        from Flight f
        where f.status not in :unavailable
        order by f.origin, f.destination
        """)
    List<RoutePair> findDistinctRoutes(@Param("unavailable") Collection<FlightStatus> unavailable);

    @Query("""
        select f
        from Flight f
        where (:o is null or lower(f.origin) = lower(:o))
          and (:d is null or lower(f.destination) = lower(:d))
          and (:dt is null or f.departureDate = :dt)
          and f.status not in :unavailable
          and (
              f.departureDate > :today
              or (
                  f.departureDate = :today
                  and f.departureTime >= cast(:time as time)
              )
          )
        order by f.departureDate, f.departureTime
        """)
    List<Flight> search(
            @Param("o") String o,
            @Param("d") String d,
            @Param("dt") LocalDate dt,
            @Param("unavailable") Collection<FlightStatus> unavailable,
            @Param("today") LocalDate today,
            @Param("time") LocalTime time
    );

    @Query("""
        select f
        from Flight f
        where f.status not in :unavailable
          and (
              f.departureDate > :today
              or (
                  f.departureDate = :today
                  and f.departureTime >= cast(:time as time)
              )
          )
        order by f.departureDate, f.departureTime
        """)
    List<Flight> findFeaturedFlights(
            @Param("today") LocalDate today,
            @Param("time") LocalTime time,
            @Param("unavailable") Collection<FlightStatus> unavailable
    );

    default List<Flight> findFeaturedFlights() {
        return findFeaturedFlights(
                LocalDate.now(),
                LocalTime.now(),
                List.of(FlightStatus.CANCELLED, FlightStatus.COMPLETED)
        ).stream()
                .limit(6)
                .toList();
    }
}