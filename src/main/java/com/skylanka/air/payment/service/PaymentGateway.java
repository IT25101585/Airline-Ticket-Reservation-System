package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.payment.entity.PaymentMethod;

public interface PaymentGateway {
    AuthorizationResult authorize(Booking booking, PaymentMethod method);

    /** Variant that also sees the card number so a sandbox gateway can simulate declines. */
    default AuthorizationResult authorize(Booking booking, PaymentMethod method, String cardNumber) {
        return authorize(booking, method);
    }

    record AuthorizationResult(boolean approved, String transactionId, String message) {}
}