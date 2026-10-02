package com.skylanka.air.payment.entity;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.shared.entity.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // A booking can receive one or more payments (e.g. a declined attempt followed by a retry).
    @ManyToOne(optional = false)
    private Booking booking;
    @Column(unique = true, nullable = false)
    private String transactionId;
    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;
    @Column(precision = 19, scale = 2)
    private BigDecimal refundAmount;
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime createdAt = LocalDateTime.now();
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking v) {
        booking = v;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String v) {
        transactionId = v;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal v) {
        amount = v;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal v) {
        refundAmount = v;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod v) {
        method = v;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus v) {
        status = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime v) {
        updatedAt = v;
    }
}
