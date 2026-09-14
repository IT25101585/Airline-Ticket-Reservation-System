package com.example.booking.client;

import java.math.BigDecimal;

public interface PaymentServiceClient {
    String initiatePayment(String bookingReference, BigDecimal amount);
    void requestRefund(String bookingReference, String paymentId, BigDecimal amount);
}