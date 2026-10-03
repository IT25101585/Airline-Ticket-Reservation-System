package com.skylanka.air.flight.repository;

import com.skylanka.air.flight.entity.Airport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long> {
    Optional<Airport> findByAirportCodeIgnoreCase(String code);

    Optional<Airport> findFirstByCityIgnoreCase(String city);
}
