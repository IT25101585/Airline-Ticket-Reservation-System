package com.skylanka.air.payment.repository;

import com.skylanka.air.payment.entity.RefundRequest;
import com.skylanka.air.shared.entity.RefundRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    List<RefundRequest> findByStatusOrderByCreatedAtAsc(RefundRequestStatus status);
    List<RefundRequest> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
    Optional<RefundRequest> findFirstByBookingIdAndStatus(Long bookingId, RefundRequestStatus status);
}
