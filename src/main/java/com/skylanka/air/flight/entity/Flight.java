package com.skylanka.air.flight.entity;

import com.skylanka.air.shared.entity.FlightStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "flights")
public class Flight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank
    private String flightNumber;

    @NotBlank
    private String airline;

    @NotBlank
    private String origin;

    @NotBlank
    private String destination;

    @NotNull
    @JdbcTypeCode(SqlTypes.DATE)
    private LocalDate departureDate;

    @NotNull
    @JdbcTypeCode(SqlTypes.TIME)
    private LocalTime departureTime;

    @NotNull
    @JdbcTypeCode(SqlTypes.TIME)
    private LocalTime arrivalTime;

    @NotNull
    @Positive
    @Column(precision = 19, scale = 2)
    private BigDecimal baseFare;

    @NotNull
    @Min(1)
    private Integer seatCapacity;

    @NotBlank
    private String aircraft;

    @Enumerated(EnumType.STRING)
    private FlightStatus status = FlightStatus.ON_TIME;

    private Integer delayMinutes;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String v) {
        flightNumber = v;
    }

    public String getAirline() {
        return airline;
    }

    public void setAirline(String v) {
        airline = v;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String v) {
        origin = v;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String v) {
        destination = v;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDate v) {
        departureDate = v;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime v) {
        departureTime = v;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalTime v) {
        arrivalTime = v;
    }

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(BigDecimal v) {
        baseFare = v;
    }

    public Integer getSeatCapacity() {
        return seatCapacity;
    }

    public void setSeatCapacity(Integer v) {
        seatCapacity = v;
    }

    public String getAircraft() {
        return aircraft;
    }

    public void setAircraft(String v) {
        aircraft = v;
    }

    public FlightStatus getStatus() {
        return status;
    }

    public void setStatus(FlightStatus v) {
        status = v;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer v) {
        delayMinutes = v;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime v) {
        updatedAt = v;
    }

    @ManyToOne
    @JoinColumn(name = "departure_airport_id")
    private com.skylanka.air.flight.entity.Airport departureAirport;
    @ManyToOne
    @JoinColumn(name = "arrival_airport_id")
    private com.skylanka.air.flight.entity.Airport arrivalAirport;
    /** Passport details are only mandatory on international flights. Derived from the airports' countries. */
    @Column(name = "is_international", nullable = false, columnDefinition = "bit not null default 1")
    private boolean international = true;

    public com.skylanka.air.flight.entity.Airport getDepartureAirport() {
        return departureAirport;
    }

    public void setDepartureAirport(com.skylanka.air.flight.entity.Airport v) {
        departureAirport = v;
    }

    public com.skylanka.air.flight.entity.Airport getArrivalAirport() {
        return arrivalAirport;
    }

    public void setArrivalAirport(com.skylanka.air.flight.entity.Airport v) {
        arrivalAirport = v;
    }

    /** Operations officers responsible for this flight (many officers per flight, many flights per officer). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "flight_operations_officers",
            joinColumns = @JoinColumn(name = "flight_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private java.util.Set<com.skylanka.air.user.entity.User> operationsOfficers = new java.util.LinkedHashSet<>();

    /** Lazy collection: only read this inside a transaction (see FlightService.officersFor). */
    public java.util.Set<com.skylanka.air.user.entity.User> getOperationsOfficers() {
        return operationsOfficers;
    }

    public void setOperationsOfficers(java.util.Set<com.skylanka.air.user.entity.User> v) {
        operationsOfficers = v;
    }

    public boolean isInternational() {
        return international;
    }

    public void setInternational(boolean v) {
        international = v;
    }
}
