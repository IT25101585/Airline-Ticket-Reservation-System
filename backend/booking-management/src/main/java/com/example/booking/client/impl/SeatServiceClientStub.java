package com.example.booking.client.impl;

import com.example.booking.client.SeatServiceClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// TEMPORARY stub. Replace with the real Seat module integration.
@Component
public class SeatServiceClientStub implements SeatServiceClient {

    @Override
    public boolean isSeatAvailable(Long flightId, Long seatId) {
        return true;
    }

    @Override
    public String holdSeat(Long flightId, Long seatId, String bookingReference) {
        return "SEAT-" + seatId;
    }

    @Override
    public void releaseSeat(Long flightId, Long seatId, String bookingReference) {
    }

    @Override
    public void confirmSeat(Long flightId, Long seatId, String bookingReference) {
    }

    @Override
    public BigDecimal getSeatSurcharge(Long flightId, Long seatId) {
        return BigDecimal.ZERO;
    }
}