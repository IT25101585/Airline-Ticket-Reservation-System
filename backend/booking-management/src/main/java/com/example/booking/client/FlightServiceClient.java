package com.example.booking.client;

import java.math.BigDecimal;

public interface FlightServiceClient {
    boolean isFlightBookable(Long flightId);
    BigDecimal getBaseFare(Long flightId);
}