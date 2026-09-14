package com.example.booking.client.impl;

import com.example.booking.client.FlightServiceClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// TEMPORARY stub. Replace with the real Flight module integration.
@Component
public class FlightServiceClientStub implements FlightServiceClient {

    @Override
    public boolean isFlightBookable(Long flightId) {
        return flightId != null && flightId > 0;
    }

    @Override
    public BigDecimal getBaseFare(Long flightId) {
        return new BigDecimal("150.00");
    }
}