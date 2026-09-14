package com.skylanka.ticketmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skylanka.ticketmanagement.enums.TicketStatus;

import java.time.LocalDateTime;

/**
 * Response returned by the ticket validation endpoint.
 * Contains enough data for check-in staff, but no sensitive payment info.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketValidationResponse {

    private boolean valid;
    private String ticketNumber;
    private String passengerName;
    private String flightNumber;
    private String origin;
    private String destination;
    private LocalDateTime departureTime;
    private String seatNumber;
    private String cabinClass;
    private String bookingReference;
    private TicketStatus ticketStatus;
    private String invalidReason;
    private LocalDateTime validationTimestamp;

    public TicketValidationResponse() {
        this.validationTimestamp = LocalDateTime.now();
    }

    // ---- factory helpers ----

    public static TicketValidationResponse invalid(String reason) {
        TicketValidationResponse r = new TicketValidationResponse();
        r.valid = false;
        r.invalidReason = reason;
        return r;
    }

    // ===== Getters / Setters =====

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public LocalDateTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getCabinClass() { return cabinClass; }
    public void setCabinClass(String cabinClass) { this.cabinClass = cabinClass; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public TicketStatus getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(TicketStatus ticketStatus) { this.ticketStatus = ticketStatus; }

    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }

    public LocalDateTime getValidationTimestamp() { return validationTimestamp; }
    public void setValidationTimestamp(LocalDateTime validationTimestamp) { this.validationTimestamp = validationTimestamp; }
}
