package com.skylanka.ticketmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/tickets/generate
 */
public class GenerateTicketRequest {

    @NotBlank(message = "Booking ID is required")
    private String bookingId;

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
}
