package com.example.booking.client.impl;

import com.example.booking.client.TicketServiceClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

// TEMPORARY stub. Replace with the real Ticket module integration.
@Component
public class TicketServiceClientStub implements TicketServiceClient {

    @Override
    public String generateTicket(String bookingReference) {
        return "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public void voidTicket(String ticketId) {
    }

    @Override
    public String reissueTicket(String bookingReference, String oldTicketId) {
        return "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}