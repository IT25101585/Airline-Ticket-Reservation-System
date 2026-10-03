package com.skylanka.air.seat.entity;

import java.time.LocalDateTime;

import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.shared.entity.SeatStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"flight_id", "seat_number"}))
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Flight flight;
    @Column(nullable = false)
    private String seatNumber;
    @Column(nullable = false)
    private String seatClass;
    @Enumerated(EnumType.STRING)
    private SeatStatus status = SeatStatus.AVAILABLE;

    public Long getId() {
        return id;
    }

    public Flight getFlight() {
        return flight;
    }

    public void setFlight(Flight v) {
        flight = v;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String v) {
        seatNumber = v;
    }

    public String getSeatClass() {
        return seatClass;
    }

    public void setSeatClass(String v) {
        seatClass = v;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus v) {
        status = v;
    }

    /** Who currently holds this seat while status == HELD: "U<userId>" or "S<sessionId>" for guests. */
    @Column(length = 80)
    private String heldBy;
    private LocalDateTime heldAt;

    public String getHeldBy() {
        return heldBy;
    }

    public void setHeldBy(String v) {
        heldBy = v;
    }

    public LocalDateTime getHeldAt() {
        return heldAt;
    }

    public void setHeldAt(LocalDateTime v) {
        heldAt = v;
    }
}
