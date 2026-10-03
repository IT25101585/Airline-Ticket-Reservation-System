package com.skylanka.air.ticket.entity;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.entity.Passenger;
import com.skylanka.air.shared.entity.TicketStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.*;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Booking booking;
    // Many tickets may exist per passenger over time (a reissue supersedes the old one).
    @ManyToOne(optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;
    /** The ticket this one replaced when it was reissued after a booking modification. */
    @ManyToOne
    @JoinColumn(name = "reissued_from_id")
    private Ticket reissuedFrom;
    @Column(unique = true, nullable = false)
    private String ticketNumber;
    private String qrValue;
    @Enumerated(EnumType.STRING)
    private TicketStatus status = TicketStatus.ISSUED;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime issuedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking v) {
        booking = v;
    }

    public Passenger getPassenger() {
        return passenger;
    }

    public void setPassenger(Passenger v) {
        passenger = v;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String v) {
        ticketNumber = v;
    }

    public String getQrValue() {
        return qrValue;
    }

    public void setQrValue(String v) {
        qrValue = v;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus v) {
        status = v;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public Ticket getReissuedFrom() {
        return reissuedFrom;
    }

    public void setReissuedFrom(Ticket v) {
        reissuedFrom = v;
    }
}
