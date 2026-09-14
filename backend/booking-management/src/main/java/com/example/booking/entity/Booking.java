//main database entity
//SQL/database mapping happens here

package com.example.booking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
//mapped to the database table: bookings
@Table(name = "bookings", uniqueConstraints = {
        @UniqueConstraint(columnNames = "bookingReference")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    //ID-->primary key-->autogenerate
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //maps to column w constraints
    @Column(nullable = false, unique = true, length = 12)
    private String bookingReference;


    //RE: PLACEHOLDER SUBSYSTEMS-------------

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long flightId;

    @Column(nullable = false)
    private Long seatId;

    private String paymentId;

    private String ticketId;


    //DATA OWNED BY THIS SUBSYSTEM-------------

    //booking-->booking_passenger (1:N)
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingPassenger> passengers = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFare;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime seatHoldExpiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addPassenger(BookingPassenger passenger) {
        passenger.setBooking(this);
        this.passengers.add(passenger);
    }
}