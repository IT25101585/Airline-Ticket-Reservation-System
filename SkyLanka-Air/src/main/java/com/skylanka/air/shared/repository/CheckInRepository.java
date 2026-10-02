package com.skylanka.air.shared.repository;

import com.skylanka.air.shared.entity.CheckIn;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    Optional<CheckIn> findByPassengerId(Long passengerId);
    List<CheckIn> findByPassengerBookingId(Long bookingId);
}
