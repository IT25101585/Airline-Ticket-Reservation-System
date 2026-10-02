package com.skylanka.air.booking.repository;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.shared.entity.BookingStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    Optional<Booking> findByReference(String reference);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long id);

    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByFlightId(Long flightId);

    long countByStatus(BookingStatus s);

    long countByCreatedAtAfter(LocalDateTime since);

    boolean existsBySeatIdAndStatusIn(
            Long seatId,
            Collection<BookingStatus> statuses
    );

    List<Booking> findByStatusAndCreatedAtBefore(
            BookingStatus status,
            LocalDateTime time
    );

    interface DestinationPopularity {
        String getDestination();
        long getBookingCount();
    }

    @Query("""
        select b.flight.destination as destination, count(b) as bookingCount
        from Booking b
        where b.status <> :excludedStatus
        group by b.flight.destination
        order by count(b) desc
        """)
    List<DestinationPopularity> destinationPopularity(@Param("excludedStatus") BookingStatus excludedStatus);

    interface CampaignPerformance {
        Long getCampaignId();
        long getBookingCount();
        java.math.BigDecimal getTotalDiscount();
    }

    /**
     * Only reflects bookings made after promo-code tracking was added to
     * Booking - existing bookings made before that won't be attributed to
     * any campaign.
     */
    @Query("""
        select pr.campaign.id as campaignId, count(b) as bookingCount, coalesce(sum(b.discount), 0) as totalDiscount
        from Booking b join Promotion pr on b.promoCode = pr.code
        where pr.campaign.id is not null
        group by pr.campaign.id
        """)
    List<CampaignPerformance> campaignPerformance();

    @Query("select distinct b.customer from Booking b where b.createdAt >= :since")
    List<com.skylanka.air.user.entity.User> findDistinctCustomersBookedSince(@Param("since") LocalDateTime since);

    interface DailyBookingCount {
        LocalDate getDay();
        long getBookingCount();
    }

    @Query("""
        select cast(b.createdAt as date) as day, count(b) as bookingCount
        from Booking b
        where b.createdAt >= :since
        group by cast(b.createdAt as date)
        order by cast(b.createdAt as date)
        """)
    List<DailyBookingCount> bookingCountByDay(@Param("since") LocalDateTime since);

    @Query("""
        select b.customer.id
        from Booking b
        where b.status <> :excludedStatus
        group by b.customer.id
        having count(b) > 1
        """)
    List<Long> repeatCustomerIds(@Param("excludedStatus") BookingStatus excludedStatus);

    @Query("""
        select b
        from Booking b
        where b.customer.id = :customerId
        and b.status in :statuses
        and (
            b.flight.departureDate > :today
            or (
                b.flight.departureDate = :today
                and b.flight.departureTime >= cast(:time as time)
            )
        )
        order by b.flight.departureDate asc,
                 b.flight.departureTime asc
        """)
    List<Booking> findUpcomingBookings(
            @Param("customerId") Long customerId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("today") LocalDate today,
            @Param("time") LocalTime time
    );
}

