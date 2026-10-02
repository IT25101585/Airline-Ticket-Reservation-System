package com.skylanka.air.payment.entity;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.shared.entity.RefundRequestStatus;
import com.skylanka.air.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "refund_requests")
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @NotBlank
    @Size(min = 10, max = 500)
    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    private RefundRequestStatus status = RefundRequestStatus.PENDING;

    @Column(length = 500)
    private String financeNote;

    @ManyToOne
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime createdAt = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime resolvedAt;

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking v) {
        booking = v;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User v) {
        customer = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        reason = v;
    }

    public RefundRequestStatus getStatus() {
        return status;
    }

    public void setStatus(RefundRequestStatus v) {
        status = v;
    }

    public String getFinanceNote() {
        return financeNote;
    }

    public void setFinanceNote(String v) {
        financeNote = v;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(User v) {
        resolvedBy = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime v) {
        resolvedAt = v;
    }
}
