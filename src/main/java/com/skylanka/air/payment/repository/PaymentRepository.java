package com.skylanka.air.payment.repository;

import com.skylanka.air.payment.entity.Payment;
import com.skylanka.air.shared.entity.PaymentStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.*;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByBookingIdOrderByIdDesc(Long id);

    List<Payment> findAllByBookingIdOrderByIdAsc(Long id);

    /**
     * A booking may have several payment attempts (e.g. a declined card followed by a successful
     * retry). Everywhere the app asks for "the" payment of a booking it means the most recent one.
     */
    default Optional<Payment> findByBookingId(Long id) {
        return findFirstByBookingIdOrderByIdDesc(id);
    }

    List<Payment> findAllByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status in :statuses")
    BigDecimal sumByStatuses(@Param("statuses") Collection<PaymentStatus> statuses);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status in :statuses and p.createdAt >= :since")
    BigDecimal sumByStatusesSince(@Param("statuses") Collection<PaymentStatus> statuses, @Param("since") java.time.LocalDateTime since);

    @Query("select coalesce(sum(coalesce(p.refundAmount, p.amount)), 0) from Payment p where p.status = :status")
    BigDecimal sumByStatus(@Param("status") PaymentStatus status);

    interface RouteRevenue {
        String getOrigin();
        String getDestination();
        BigDecimal getRevenue();
    }

    @Query("""
        select b.flight.origin as origin, b.flight.destination as destination,
               coalesce(sum(p.amount), 0) as revenue
        from Payment p join p.booking b
        where p.status in :statuses
        group by b.flight.origin, b.flight.destination
        order by sum(p.amount) desc
        """)
    List<RouteRevenue> revenueByRoute(@Param("statuses") Collection<PaymentStatus> statuses);
}