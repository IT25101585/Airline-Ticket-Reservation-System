package com.skylanka.air.shared.entity;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.entity.Passenger;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.*;

@Entity
@Table(name = "check_ins")
public class CheckIn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false)
    @JoinColumn(name = "passenger_id", nullable = false, unique = true)
    private Passenger passenger;
    private boolean checkedIn;
    private boolean noShow;
    private String boardingPassNumber;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime checkedAt;

    public Long getId() {
        return id;
    }

    public Passenger getPassenger() {
        return passenger;
    }

    public void setPassenger(Passenger v) {
        passenger = v;
    }

    public Booking getBooking() {
        return passenger == null ? null : passenger.getBooking();
    }

    public boolean isCheckedIn() {
        return checkedIn;
    }

    public void setCheckedIn(boolean v) {
        checkedIn = v;
    }

    public boolean isNoShow() {
        return noShow;
    }

    public void setNoShow(boolean v) {
        noShow = v;
    }

    public String getBoardingPassNumber() {
        return boardingPassNumber;
    }

    public void setBoardingPassNumber(String v) {
        boardingPassNumber = v;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime v) {
        checkedAt = v;
    }
}
