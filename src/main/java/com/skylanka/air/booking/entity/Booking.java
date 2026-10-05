package com.skylanka.air.booking.entity;

import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.seat.entity.Seat;
import com.skylanka.air.user.entity.User;
import com.skylanka.air.shared.entity.BookingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String reference;
    @ManyToOne(optional = false)
    private User customer;
    @ManyToOne(optional = false)
    private Flight flight;
    @ManyToOne(optional = false)
    private Seat seat;
    @NotBlank
    @Size(min = 2, max = 100)
    @Pattern(regexp = "[\\p{L} .'-]+", message = "Passenger name may only contain letters, spaces, apostrophes, periods and hyphens.")
    @Column(nullable = false, length = 100)
    private String passengerName;
    @Pattern(regexp = "([A-Za-z0-9]{6,9})?", message = "Enter a valid passport number (6-9 letters/digits).")
    @Column(nullable = false, length = 9)
    private String passportNumber;
    @NotBlank
    @Pattern(regexp = "\\+?[0-9]{7,15}", message = "Enter a valid contact number (7-15 digits, optional leading +).")
    @Column(nullable = false, length = 20)
    private String passengerContact;
    private String seatClass;
    @Column(precision = 19, scale = 2)
    private BigDecimal baseFare;
    @Column(precision = 19, scale = 2)
    private BigDecimal tax;
    @Column(precision = 19, scale = 2)
    private BigDecimal serviceFee;
    @Column(precision = 19, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;
    @Column(length = 50)
    private String promoCode;
    @Column(precision = 19, scale = 2)
    private BigDecimal totalFare;
    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.PENDING;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime createdAt = LocalDateTime.now();
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime modifiedAt;
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private java.util.List<Passenger> passengers = new java.util.ArrayList<>();

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String v) {
        reference = v;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User v) {
        customer = v;
    }

    public Flight getFlight() {
        return flight;
    }

    public void setFlight(Flight v) {
        flight = v;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat v) {
        seat = v;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String v) {
        passengerName = v;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String v) {
        passportNumber = v;
    }

    public String getPassengerContact() {
        return passengerContact;
    }

    public void setPassengerContact(String v) {
        passengerContact = v;
    }

    public String getSeatClass() {
        return seatClass;
    }

    public void setSeatClass(String v) {
        seatClass = v;
    }

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(BigDecimal v) {
        baseFare = v;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal v) {
        tax = v;
    }

    public BigDecimal getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(BigDecimal v) {
        serviceFee = v;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal v) {
        discount = v;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String v) {
        promoCode = v;
    }

    public BigDecimal getTotalFare() {
        return totalFare;
    }

    public void setTotalFare(BigDecimal v) {
        totalFare = v;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus v) {
        status = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(LocalDateTime v) {
        modifiedAt = v;
    }

    public java.util.List<Passenger> getPassengers() {
        return passengers;
    }

    public void setPassengers(java.util.List<Passenger> v) {
        passengers = v;
    }

    public int getPassengerCount() {
        return passengers == null || passengers.isEmpty() ? 1 : passengers.size();
    }
}
