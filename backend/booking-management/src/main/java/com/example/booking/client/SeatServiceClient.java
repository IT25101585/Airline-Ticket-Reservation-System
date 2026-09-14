package com.example.booking.client;

import java.math.BigDecimal;

public interface SeatServiceClient {
    boolean isSeatAvailable(Long flightId, Long seatId);
    String holdSeat(Long flightId, Long seatId, String bookingReference);
    void releaseSeat(Long flightId, Long seatId, String bookingReference);
    void confirmSeat(Long flightId, Long seatId, String bookingReference);
    BigDecimal getSeatSurcharge(Long flightId, Long seatId);
}