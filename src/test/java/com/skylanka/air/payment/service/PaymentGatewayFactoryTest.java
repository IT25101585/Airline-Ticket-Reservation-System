package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.payment.entity.PaymentMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayFactoryTest {

    private final PaymentGatewayFactory factory =
            new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway());

    @Test
    void cardMethodsUseTheCardGateway() {
        for (PaymentMethod method : new PaymentMethod[]{PaymentMethod.VISA, PaymentMethod.MASTERCARD}) {
            var result = factory.forMethod(method).authorize(new Booking(), method, "4242424242424242");
            assertTrue(result.approved());
            assertTrue(result.transactionId().startsWith("MOCK-TX-"));
        }
    }

    @Test
    void bankTransferUsesTheBankTransferGateway() {
        var result = factory.forMethod(PaymentMethod.BANK_TRANSFER)
                .authorize(new Booking(), PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.approved());
        assertTrue(result.transactionId().startsWith("BANK-TX-"));
    }

    @Test
    void sandboxDeclineCardIsStillDeclinedThroughTheDecorator() {
        var result = factory.forMethod(PaymentMethod.VISA)
                .authorize(new Booking(), PaymentMethod.VISA, "4000 0000 0000 0002");
        assertFalse(result.approved());
        assertNull(result.transactionId());
    }

    @Test
    void missingMethodIsReportedAsUnsupported() {
        var result = factory.forMethod(null).authorize(new Booking(), null);
        assertFalse(result.approved());
    }

    @Test
    void decoratorReturnsTheWrappedGatewaysResultUnchanged() {
        PaymentGateway inner = new BankTransferGateway();
        PaymentGateway decorated = new AuditingPaymentGateway(inner);

        assertFalse(decorated.authorize(new Booking(), PaymentMethod.VISA).approved());
        assertTrue(decorated.authorize(new Booking(), PaymentMethod.BANK_TRANSFER).approved());
    }

    @Test
    void bankTransferGatewayRejectsCardMethods() {
        assertFalse(new BankTransferGateway().authorize(new Booking(), PaymentMethod.VISA).approved());
    }
}
