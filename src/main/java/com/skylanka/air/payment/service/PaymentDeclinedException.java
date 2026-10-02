package com.skylanka.air.payment.service;

/**
 * Thrown when the payment gateway declines a payment. The declined attempt is still recorded
 * (status FAILED) - the transaction is not rolled back - and the customer may retry.
 */
public class PaymentDeclinedException extends IllegalStateException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
