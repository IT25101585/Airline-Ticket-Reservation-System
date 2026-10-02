package com.skylanka.air.shared.repository;

import com.skylanka.air.shared.entity.Aircraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AircraftRepository extends JpaRepository<Aircraft, Long> {
    Optional<Aircraft> findByTailNumber(String tailNumber);
    Optional<Aircraft> findFirstByModelIgnoreCase(String model);
}
