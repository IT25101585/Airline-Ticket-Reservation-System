package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.payment.entity.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator: wraps any {@link PaymentGateway} and records every authorization attempt
 * (method, booking reference, outcome, transaction id) without changing the result.
 * Card numbers are never logged. Because it implements the same interface, it can wrap
 * the card gateway, the bank-transfer gateway, or a future real provider unchanged.
 */
public class AuditingPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger("payment-audit");

    private final PaymentGateway delegate;

    public AuditingPaymentGateway(PaymentGateway delegate) {
        this.delegate = delegate;
    }

    @Override
    public AuthorizationResult authorize(Booking booking, PaymentMethod method) {
        return record(booking, method, delegate.authorize(booking, method));
    }

    @Override
    public AuthorizationResult authorize(Booking booking, PaymentMethod method, String cardNumber) {
        return record(booking, method, delegate.authorize(booking, method, cardNumber));
    }

    private AuthorizationResult record(Booking booking, PaymentMethod method, AuthorizationResult result) {
        log.info("payment attempt booking={} method={} gateway={} approved={} tx={}",
                booking == null ? null : booking.getReference(),
                method,
                delegate.getClass().getSimpleName(),
                result.approved(),
                result.transactionId());
        return result;
    }
}
