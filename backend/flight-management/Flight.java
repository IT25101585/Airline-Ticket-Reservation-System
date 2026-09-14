package com.airline.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Model entity representing a Flight record in the database.
 * Includes data encapsulation, constructors, getters/setters, and domain validation.
 */
public class Flight implements Serializable {
    private static final long serialVersionUID = 1L;

    private int flightId;
    private String flightNumber;
    private String airlineName;
    private String departureAirport;
    private String arrivalAirport;
    private String departureCity;
    private String arrivalCity;
    private String departureDate;   // Format: YYYY-MM-DD
    private String departureTime;   // Format: HH:MM:SS or HH:MM
    private String arrivalDate;     // Format: YYYY-MM-DD
    private String arrivalTime;     // Format: HH:MM:SS or HH:MM
    private String aircraftType;
    private int totalSeats;
    private int availableSeats;
    private BigDecimal ticketPrice;
    private String status;          // 'Scheduled', 'Delayed', 'Departed', 'Arrived', 'Cancelled'
    private String createdAt;
    private String updatedAt;

    public static final List<String> VALID_STATUSES = Arrays.asList(
            "Scheduled", "Delayed", "Departed", "Arrived", "Cancelled"
    );

    // Default No-argument Constructor
    public Flight() {
        this.status = "Scheduled";
    }

    // Full Parameterized Constructor (without generated timestamps)
    public Flight(int flightId, String flightNumber, String airlineName, String departureAirport,
                  String arrivalAirport, String departureCity, String arrivalCity,
                  String departureDate, String departureTime, String arrivalDate,
                  String arrivalTime, String aircraftType, int totalSeats,
                  int availableSeats, BigDecimal ticketPrice, String status) {
        this.flightId = flightId;
        this.flightNumber = flightNumber;
        this.airlineName = airlineName;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.departureCity = departureCity;
        this.arrivalCity = arrivalCity;
        this.departureDate = departureDate;
        this.departureTime = departureTime;
        this.arrivalDate = arrivalDate;
        this.arrivalTime = arrivalTime;
        this.aircraftType = aircraftType;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.ticketPrice = ticketPrice;
        this.status = (status != null && !status.trim().isEmpty()) ? status : "Scheduled";
    }

    // Constructor for creating new flights (before flightId is assigned by MySQL AUTO_INCREMENT)
    public Flight(String flightNumber, String airlineName, String departureAirport,
                  String arrivalAirport, String departureCity, String arrivalCity,
                  String departureDate, String departureTime, String arrivalDate,
                  String arrivalTime, String aircraftType, int totalSeats,
                  int availableSeats, BigDecimal ticketPrice, String status) {
        this(0, flightNumber, airlineName, departureAirport, arrivalAirport, departureCity, arrivalCity,
                departureDate, departureTime, arrivalDate, arrivalTime, aircraftType, totalSeats,
                availableSeats, ticketPrice, status);
    }

    // --- Domain Validation Method ---
    /**
     * Validates all flight attributes against business and database rules.
     * @return List of error messages; empty if validation passes.
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (flightNumber == null || flightNumber.trim().isEmpty()) {
            errors.add("Flight number is required.");
        } else if (!flightNumber.trim().matches("^[A-Z0-9-]{2,15}$")) {
            errors.add("Flight number must be 2-15 characters (letters, digits, or hyphen).");
        }

        if (airlineName == null || airlineName.trim().isEmpty()) {
            errors.add("Airline name is required.");
        }

        if (departureAirport == null || departureAirport.trim().isEmpty()) {
            errors.add("Departure airport code is required.");
        }

        if (arrivalAirport == null || arrivalAirport.trim().isEmpty()) {
            errors.add("Arrival airport code is required.");
        }

        if (departureAirport != null && arrivalAirport != null &&
                departureAirport.trim().equalsIgnoreCase(arrivalAirport.trim())) {
            errors.add("Departure and arrival airports cannot be identical.");
        }

        if (departureCity == null || departureCity.trim().isEmpty()) {
            errors.add("Departure city is required.");
        }

        if (arrivalCity == null || arrivalCity.trim().isEmpty()) {
            errors.add("Arrival city is required.");
        }

        if (departureCity != null && arrivalCity != null &&
                departureCity.trim().equalsIgnoreCase(arrivalCity.trim())) {
            errors.add("Departure and arrival cities cannot be identical.");
        }

        // Validate dates and times
        LocalDate depDate = null;
        LocalDate arrDate = null;
        LocalTime depTime = null;
        LocalTime arrTime = null;

        if (departureDate == null || departureDate.trim().isEmpty()) {
            errors.add("Departure date is required.");
        } else {
            try {
                depDate = LocalDate.parse(departureDate.trim());
            } catch (DateTimeParseException e) {
                errors.add("Departure date must be in YYYY-MM-DD format.");
            }
        }

        if (arrivalDate == null || arrivalDate.trim().isEmpty()) {
            errors.add("Arrival date is required.");
        } else {
            try {
                arrDate = LocalDate.parse(arrivalDate.trim());
            } catch (DateTimeParseException e) {
                errors.add("Arrival date must be in YYYY-MM-DD format.");
            }
        }

        if (departureTime == null || departureTime.trim().isEmpty()) {
            errors.add("Departure time is required.");
        } else {
            try {
                String t = departureTime.trim();
                if (t.length() == 5) t += ":00";
                depTime = LocalTime.parse(t);
            } catch (DateTimeParseException e) {
                errors.add("Departure time must be in HH:MM or HH:MM:SS format.");
            }
        }

        if (arrivalTime == null || arrivalTime.trim().isEmpty()) {
            errors.add("Arrival time is required.");
        } else {
            try {
                String t = arrivalTime.trim();
                if (t.length() == 5) t += ":00";
                arrTime = LocalTime.parse(t);
            } catch (DateTimeParseException e) {
                errors.add("Arrival time must be in HH:MM or HH:MM:SS format.");
            }
        }

        // Chronological consistency check
        if (depDate != null && arrDate != null) {
            if (arrDate.isBefore(depDate)) {
                errors.add("Arrival date cannot be before departure date.");
            } else if (arrDate.isEqual(depDate) && depTime != null && arrTime != null) {
                if (!arrTime.isAfter(depTime)) {
                    errors.add("Arrival time must be after departure time for same-day flights.");
                }
            }
        }

        if (aircraftType == null || aircraftType.trim().isEmpty()) {
            errors.add("Aircraft type is required.");
        }

        if (totalSeats <= 0) {
            errors.add("Total seats must be greater than zero.");
        }

        if (availableSeats < 0) {
            errors.add("Available seats cannot be negative.");
        }

        if (availableSeats > totalSeats) {
            errors.add("Available seats cannot exceed total seats.");
        }

        if (ticketPrice == null || ticketPrice.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Ticket price must be a non-negative amount.");
        }

        if (status == null || !VALID_STATUSES.contains(status.trim())) {
            errors.add("Status must be one of: " + String.join(", ", VALID_STATUSES));
        }

        return errors;
    }

    // --- Getters and Setters ---
    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getAirlineName() {
        return airlineName;
    }

    public void setAirlineName(String airlineName) {
        this.airlineName = airlineName;
    }

    public String getDepartureAirport() {
        return departureAirport;
    }

    public void setDepartureAirport(String departureAirport) {
        this.departureAirport = departureAirport;
    }

    public String getArrivalAirport() {
        return arrivalAirport;
    }

    public void setArrivalAirport(String arrivalAirport) {
        this.arrivalAirport = arrivalAirport;
    }

    public String getDepartureCity() {
        return departureCity;
    }

    public void setDepartureCity(String departureCity) {
        this.departureCity = departureCity;
    }

    public String getArrivalCity() {
        return arrivalCity;
    }

    public void setArrivalCity(String arrivalCity) {
        this.arrivalCity = arrivalCity;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(String departureDate) {
        this.departureDate = departureDate;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(String arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public BigDecimal getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(BigDecimal ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Flight{" +
                "flightId=" + flightId +
                ", flightNumber='" + flightNumber + '\'' +
                ", airlineName='" + airlineName + '\'' +
                ", route=" + departureAirport + " (" + departureCity + ") -> " + arrivalAirport + " (" + arrivalCity + ")" +
                ", departure=" + departureDate + " " + departureTime +
                ", arrival=" + arrivalDate + " " + arrivalTime +
                ", aircraftType='" + aircraftType + '\'' +
                ", seats=" + availableSeats + "/" + totalSeats +
                ", price=" + ticketPrice +
                ", status='" + status + '\'' +
                '}';
    }
}
