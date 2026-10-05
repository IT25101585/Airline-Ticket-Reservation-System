package com.skylanka.air.booking.entity;

import com.skylanka.air.seat.entity.Seat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "booking_passengers")
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @NotBlank
    @Size(min = 2, max = 100)
    @Pattern(regexp = "[\\p{L} .'-]+", message = "Passenger name may only contain letters, spaces, apostrophes, periods and hyphens.")
    @Column(name = "full_name", nullable = false, length = 100)
    private String name;

    @Pattern(regexp = "([A-Za-z0-9]{6,9})?", message = "Enter a valid passport number (6-9 letters/digits).")
    @Column(name = "passport_number", nullable = false, length = 9)
    private String passportNumber;

    @NotBlank
    @Pattern(regexp = "\\+?[0-9]{7,15}", message = "Enter a valid contact number (7-15 digits, optional leading +).")
    @Column(name = "contact_number", nullable = false, length = 20)
    private String contact;

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking v) {
        booking = v;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat v) {
        seat = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String v) {
        passportNumber = v;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String v) {
        contact = v;
    }
}
