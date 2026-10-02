package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.flight.entity.Flight;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RefundPolicyServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 10, 0);

    @Test
    void givesFullRefundForEarlyCustomerCancellation() {
        Booking booking = bookingDepartingAt(NOW.plusHours(49));

        var decision = policy().determine(
                booking,
                RefundPolicyService.CancellationReason.CUSTOMER_REQUEST
        );

        assertEquals(new BigDecimal("100.00"), decision.amount());
    }

    @Test
    void givesHalfRefundBetweenTwentyFourAndFortyEightHours() {
        Booking booking = bookingDepartingAt(NOW.plusHours(36));

        var decision = policy().determine(
                booking,
                RefundPolicyService.CancellationReason.CUSTOMER_REQUEST
        );

        assertEquals(new BigDecimal("50.00"), decision.amount());
    }

    @Test
    void givesNoRefundForLateCustomerCancellation() {
        Booking booking = bookingDepartingAt(NOW.plusHours(12));

        var decision = policy().determine(
                booking,
                RefundPolicyService.CancellationReason.CUSTOMER_REQUEST
        );

        assertEquals(new BigDecimal("0.00"), decision.amount());
    }

    @Test
    void givesFullRefundWhenFlightIsCancelled() {
        Booking booking = bookingDepartingAt(NOW.plusHours(2));

        var decision = policy().determine(
                booking,
                RefundPolicyService.CancellationReason.FLIGHT_CANCELLED
        );

        assertEquals(new BigDecimal("100.00"), decision.amount());
    }

    private RefundPolicyService policy() {
        return new RefundPolicyService(
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
        );
    }

    private Booking bookingDepartingAt(LocalDateTime departure) {
        Booking booking = mock(Booking.class);
        Flight flight = mock(Flight.class);
        when(booking.getTotalFare()).thenReturn(new BigDecimal("100.00"));
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getDepartureDate()).thenReturn(departure.toLocalDate());
        when(flight.getDepartureTime()).thenReturn(departure.toLocalTime());
        return booking;
    }
}