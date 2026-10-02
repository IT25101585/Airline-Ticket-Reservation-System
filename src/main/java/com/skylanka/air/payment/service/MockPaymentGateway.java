package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.payment.entity.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Strategy: sandbox gateway for card payments (Visa, Mastercard). Chosen by PaymentGatewayFactory.
 * Replace or add a gateway behind PaymentGateway when a real provider is approved.
 */
@Service
public class MockPaymentGateway implements PaymentGateway {
    private final String configuredGateway;

    public MockPaymentGateway(@Value("${payments.gateway:mock}") String configuredGateway) {
        this.configuredGateway = configuredGateway;
    }

    /** Sandbox test card that is always declined (any spacing). */
    public static final String DECLINE_TEST_CARD = "4000000000000002";

    @Override
    public AuthorizationResult authorize(Booking booking, PaymentMethod method, String cardNumber) {
        if (cardNumber != null && cardNumber.replace(" ", "").equals(DECLINE_TEST_CARD)) {
            return new AuthorizationResult(false, null,
                    "Payment declined by the issuing bank. Please check your details or try another payment method.");
        }
        return authorize(booking, method);
    }

    @Override
    public AuthorizationResult authorize(Booking booking, PaymentMethod method) {
        if (!"mock".equalsIgnoreCase(configuredGateway)) {
            return new AuthorizationResult(false, null,
                    "Payment gateway '" + configuredGateway + "' is not installed. Mock gateway is active.");
        }
        if (method == null) {
            return new AuthorizationResult(false, null, "Unsupported payment method.");
        }
        return new AuthorizationResult(
                true,
                "MOCK-TX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
                "Approved by the SkyLanka Air mock payment gateway.");
    }
}
