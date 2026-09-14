package com.skylanka.ticketmanagement.entity.stubs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * STUB entity for the Flight table owned by the Flight Management module.
 */
@Getter
@Setter
@Entity
@Table(name = "flights")
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flight_number", nullable = false, unique = true, length = 20)
    private String flightNumber;

    @Column(name = "origin_code", nullable = false, length = 10)
    private String originCode;

    @Column(name = "origin_name", length = 100)
    private String originName;

    @Column(name = "destination_code", nullable = false, length = 10)
    private String destinationCode;

    @Column(name = "destination_name", length = 100)
    private String destinationName;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
