package com.example.booking.client.impl;

import com.example.booking.client.PaymentServiceClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

// TEMPORARY stub. Replace with the real Payment module integration.
@Component
public class PaymentServiceClientStub implements PaymentServiceClient {

    @Override
    public String initiatePayment(String bookingReference, BigDecimal amount) {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public void requestRefund(String bookingReference, String paymentId, BigDecimal amount) {
    }
}