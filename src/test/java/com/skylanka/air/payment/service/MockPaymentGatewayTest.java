package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.payment.entity.PaymentMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockPaymentGatewayTest {
    @Test
    void clearlyIdentifiesSandboxTransactions() {
        var result = new MockPaymentGateway("mock").authorize(new Booking(), PaymentMethod.VISA);
        assertTrue(result.approved());
        assertTrue(result.transactionId().startsWith("MOCK-TX-"));
        assertTrue(result.message().contains("mock payment gateway"));
    }

    @Test
    void rejectsMissingMethod() {
        // An unsupported method string ("Cash", say) can no longer reach this
        // gateway at all - PaymentMethod being an enum means Spring's request
        // binding rejects it upstream. The remaining invalid case here is a
        // missing method.
        var result = new MockPaymentGateway("mock").authorize(new Booking(), null);
        assertFalse(result.approved());
        assertNull(result.transactionId());
    }

    @org.junit.jupiter.api.Test
    void sandboxDeclineCardIsDeclinedButOtherCardsAreApproved() {
        var gateway = new MockPaymentGateway("mock");
        var declined = gateway.authorize(new Booking(), PaymentMethod.VISA, "4000 0000 0000 0002");
        org.junit.jupiter.api.Assertions.assertFalse(declined.approved());
        var approved = gateway.authorize(new Booking(), PaymentMethod.VISA, "4242424242424242");
        org.junit.jupiter.api.Assertions.assertTrue(approved.approved());
    }
}
