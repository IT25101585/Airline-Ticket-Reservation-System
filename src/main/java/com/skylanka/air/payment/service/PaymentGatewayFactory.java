package com.skylanka.air.payment.service;

import com.skylanka.air.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

/**
 * Factory: chooses the right {@link PaymentGateway} strategy for a payment method, so
 * PaymentService never needs to know which concrete gateway exists. Every gateway it
 * hands out is wrapped in an {@link AuditingPaymentGateway} (Decorator).
 */
@Component
public class PaymentGatewayFactory {

    private final PaymentGateway card;
    private final PaymentGateway bankTransfer;

    public PaymentGatewayFactory(MockPaymentGateway card, BankTransferGateway bankTransfer) {
        this.card = new AuditingPaymentGateway(card);
        this.bankTransfer = new AuditingPaymentGateway(bankTransfer);
    }

    /** Gateway for the method; a missing method gets the card gateway, which reports it as unsupported. */
    public PaymentGateway forMethod(PaymentMethod method) {
        if (method == PaymentMethod.BANK_TRANSFER) {
            return bankTransfer;
        }
        return card;
    }
}
