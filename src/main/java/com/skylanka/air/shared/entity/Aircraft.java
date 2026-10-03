package com.skylanka.air.shared.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "aircraft")
public class Aircraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String tailNumber;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String model;

    @Positive
    @Column(nullable = false)
    private int seatCapacity;

    @Enumerated(EnumType.STRING)
    private AircraftStatus status = AircraftStatus.ACTIVE;

    /** Cabin layout used when seats are generated for a new flight on this aircraft. */
    public static final int DEFAULT_SEATS_PER_ROW = 6;
    public static final int MIN_SEATS_PER_ROW = 2;
    public static final int MAX_SEATS_PER_ROW = 10;
    public static final int DEFAULT_BUSINESS_SEATS = 6;

    // Nullable so databases created before layouts existed keep working; the getters supply the defaults.
    @Column(name = "seats_per_row")
    private Integer seatsPerRow = DEFAULT_SEATS_PER_ROW;

    @Column(name = "business_seats")
    private Integer businessSeats = DEFAULT_BUSINESS_SEATS;

    public Long getId() {
        return id;
    }

    public String getTailNumber() {
        return tailNumber;
    }

    public void setTailNumber(String v) {
        tailNumber = v;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String v) {
        model = v;
    }

    public int getSeatCapacity() {
        return seatCapacity;
    }

    public void setSeatCapacity(int v) {
        seatCapacity = v;
    }

    public int getSeatsPerRow() {
        return seatsPerRow == null ? DEFAULT_SEATS_PER_ROW : seatsPerRow;
    }

    public void setSeatsPerRow(int v) {
        seatsPerRow = v;
    }

    /** Business-class seats at the front of the cabin, never more than the aircraft's capacity. */
    public int getBusinessSeats() {
        int configured = businessSeats == null ? DEFAULT_BUSINESS_SEATS : businessSeats;
        return Math.max(0, Math.min(configured, seatCapacity));
    }

    public void setBusinessSeats(int v) {
        businessSeats = v;
    }

    public AircraftStatus getStatus() {
        return status;
    }

    public void setStatus(AircraftStatus v) {
        status = v;
    }
}
