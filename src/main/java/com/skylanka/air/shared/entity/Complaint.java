package com.skylanka.air.shared.entity;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.*;

@Entity
@Table(name = "complaints")
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User customer;
    @ManyToOne
    private Booking booking;
    private String subject;
    @Column(length = 2000)
    private String description;
    @Enumerated(EnumType.STRING)
    private ComplaintStatus status = ComplaintStatus.OPEN;
    private String response;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime createdAt = LocalDateTime.now();
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User v) {
        customer = v;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking v) {
        booking = v;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String v) {
        subject = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public void setStatus(ComplaintStatus v) {
        status = v;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String v) {
        response = v;
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

    /** Customer-facing tracking number, e.g. CMP-7F3A9C21. Null only for rows created before this field existed. */
    @Column(length = 30)
    private String reference;

    @PrePersist
    void assignReference() {
        if (reference == null) {
            reference = "CMP-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        }
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String v) {
        reference = v;
    }

    /** Reference to show in the UI - falls back to an id-based one for legacy rows. */
    public String getDisplayReference() {
        if (reference != null) return reference;
        return id == null ? "-" : String.format("CMP-%06d", id);
    }
}
