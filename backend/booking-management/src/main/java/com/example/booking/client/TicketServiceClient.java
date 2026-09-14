package com.example.booking.client;

public interface TicketServiceClient {
    String generateTicket(String bookingReference);
    void voidTicket(String ticketId);
    String reissueTicket(String bookingReference, String oldTicketId);
}