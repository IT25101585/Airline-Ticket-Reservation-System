package com.skylanka.air.shared.pdf;

import java.util.List;

/**
 * Plain, pre-formatted values for the e-ticket PDF. Built by TicketService from the real
 * Ticket/Booking/Flight/Passenger rows; null or blank values are simply not printed.
 */
public record TicketPdfData(
        // booking
        String pnr, String bookingId, String bookingDate, String bookingStatus,
        // passenger
        String passengerName, String passengerId, String passportNumber, String contact, String email,
        // flight
        String flightNumber, String airline, String aircraft, String flightStatus,
        String originCode, String originCity, String originAirport,
        String destCode, String destCity, String destAirport,
        String date, String departureTime, String arrivalTime,
        // seat
        String seatNumber, String seatClass,
        // ticket
        String ticketNumber, String ticketStatus, String issueDate, String reissuedFrom, String qrValue,
        // fare (already formatted, without currency)
        String currency, String baseFare, String tax, String serviceFee, String discount,
        String totalFare, String fareShare, int passengerCount,
        List<Companion> companions) {

    public record Companion(String name, String seat) {
    }
}
