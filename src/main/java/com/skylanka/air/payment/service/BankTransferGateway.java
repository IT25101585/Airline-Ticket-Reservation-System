package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.payment.entity.PaymentMethod;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Strategy: sandbox gateway for {@link PaymentMethod#BANK_TRANSFER}. It is interchangeable
 * with {@link MockPaymentGateway} (the card strategy) behind the {@link PaymentGateway}
 * interface, but issues bank-style references and has no card-decline rule.
 */
@Service
public class BankTransferGateway implements PaymentGateway {

    @Override
    public AuthorizationResult authorize(Booking booking, PaymentMethod method) {
        if (method != PaymentMethod.BANK_TRANSFER) {
            return new AuthorizationResult(false, null, "The bank transfer gateway only handles bank transfers.");
        }
        return new AuthorizationResult(
                true,
                "BANK-TX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
                "Approved by the SkyLanka Air sandbox bank-transfer gateway.");
    }
}
