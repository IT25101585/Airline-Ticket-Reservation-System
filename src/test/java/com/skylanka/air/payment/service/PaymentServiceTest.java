package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.payment.entity.Payment;
import com.skylanka.air.payment.entity.PaymentMethod;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.seat.repository.SeatRepository;
import com.skylanka.air.shared.entity.BookingStatus;
import com.skylanka.air.shared.event.DomainEventPublisher;
import com.skylanka.air.ticket.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository payments;
    @Mock BookingRepository bookings;
    @Mock SeatRepository seats;
    @Mock TicketService tickets;

    @Test
    void paymentConfirmsBookingAndIssuesTicket() {
        Booking booking = mock(Booking.class);
        com.skylanka.air.flight.entity.Flight flight = mock(com.skylanka.air.flight.entity.Flight.class);
        Payment saved = new Payment();
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getId()).thenReturn(10L);
        when(booking.getTotalFare()).thenReturn(new BigDecimal("1150.00"));
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getStatus()).thenReturn(com.skylanka.air.shared.entity.FlightStatus.ON_TIME);
        when(flight.getDepartureDate()).thenReturn(java.time.LocalDate.now().plusDays(1));
        when(flight.getDepartureTime()).thenReturn(java.time.LocalTime.NOON);
        when(payments.findByBookingId(10L)).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenReturn(saved);

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        Payment result = service.pay(booking, PaymentMethod.VISA, "4242424242424242", "12/30", "123", null);

        assertSame(saved, result);
        verify(booking).setStatus(BookingStatus.CONFIRMED);
        verify(bookings).save(booking);
        verify(tickets).issue(booking);
        verify(payments).save(any(Payment.class));
    }

    @Test
    void rejectsMissingPaymentMethod() {
        // "Cash" (or any other unsupported string) can no longer even be
        // constructed as a PaymentMethod - Spring's request binding rejects
        // it before this service is reached. The one remaining invalid case
        // the service itself has to guard against is a missing method.
        Booking booking = mock(Booking.class);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalArgumentException.class, () ->
                service.pay(booking, null)
        );
        verifyNoInteractions(payments, bookings, tickets);
    }

    @Test
    void rejectsPaymentForDepartedFlight() {
        Booking booking = mock(Booking.class);
        com.skylanka.air.flight.entity.Flight flight = mock(com.skylanka.air.flight.entity.Flight.class);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getStatus()).thenReturn(com.skylanka.air.shared.entity.FlightStatus.ON_TIME);
        when(flight.getDepartureDate()).thenReturn(java.time.LocalDate.now().minusDays(1));
        when(flight.getDepartureTime()).thenReturn(java.time.LocalTime.NOON);

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalStateException.class, () ->
                service.pay(booking, PaymentMethod.VISA)
        );
        verifyNoInteractions(payments, bookings, tickets);
    }

    @Test
    void rejectsPaymentForCancelledFlight() {
        Booking booking = mock(Booking.class);
        com.skylanka.air.flight.entity.Flight flight = mock(com.skylanka.air.flight.entity.Flight.class);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getStatus()).thenReturn(com.skylanka.air.shared.entity.FlightStatus.CANCELLED);

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalStateException.class, () ->
                service.pay(booking, PaymentMethod.VISA)
        );
        verifyNoInteractions(payments, bookings, tickets);
    }

    @Test
    void rejectsPaymentWithNoAmountDue() {
        Booking booking = mock(Booking.class);
        com.skylanka.air.flight.entity.Flight flight = mock(com.skylanka.air.flight.entity.Flight.class);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getStatus()).thenReturn(com.skylanka.air.shared.entity.FlightStatus.ON_TIME);
        when(flight.getDepartureDate()).thenReturn(java.time.LocalDate.now().plusDays(1));
        when(flight.getDepartureTime()).thenReturn(java.time.LocalTime.NOON);
        when(booking.getTotalFare()).thenReturn(BigDecimal.ZERO);

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalStateException.class, () ->
                service.pay(booking, PaymentMethod.VISA)
        );
        verifyNoInteractions(payments, bookings, tickets);
    }

    @Test
    void rejectsCardPaymentWithMissingCardDetails() {
        Booking booking = mock(Booking.class);
        com.skylanka.air.flight.entity.Flight flight = mock(com.skylanka.air.flight.entity.Flight.class);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getStatus()).thenReturn(com.skylanka.air.shared.entity.FlightStatus.ON_TIME);
        when(flight.getDepartureDate()).thenReturn(java.time.LocalDate.now().plusDays(1));
        when(flight.getDepartureTime()).thenReturn(java.time.LocalTime.NOON);
        when(booking.getTotalFare()).thenReturn(new BigDecimal("1150.00"));

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalArgumentException.class, () ->
                service.pay(booking, PaymentMethod.VISA, null, null, null, null)
        );
        verifyNoInteractions(payments, bookings, tickets);
    }

    @Test
    void rejectsCardPaymentWithExpiredCard() {
        Booking booking = mock(Booking.class);
        com.skylanka.air.flight.entity.Flight flight = mock(com.skylanka.air.flight.entity.Flight.class);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getStatus()).thenReturn(com.skylanka.air.shared.entity.FlightStatus.ON_TIME);
        when(flight.getDepartureDate()).thenReturn(java.time.LocalDate.now().plusDays(1));
        when(flight.getDepartureTime()).thenReturn(java.time.LocalTime.NOON);
        when(booking.getTotalFare()).thenReturn(new BigDecimal("1150.00"));

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalArgumentException.class, () ->
                service.pay(booking, PaymentMethod.VISA, "4242424242424242", "01/20", "123", null)
        );
        verifyNoInteractions(payments, bookings, tickets);
    }

    @Test
    void rejectsBankTransferWithMissingAccountNumber() {
        Booking booking = mock(Booking.class);
        com.skylanka.air.flight.entity.Flight flight = mock(com.skylanka.air.flight.entity.Flight.class);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getStatus()).thenReturn(com.skylanka.air.shared.entity.FlightStatus.ON_TIME);
        when(flight.getDepartureDate()).thenReturn(java.time.LocalDate.now().plusDays(1));
        when(flight.getDepartureTime()).thenReturn(java.time.LocalTime.NOON);
        when(booking.getTotalFare()).thenReturn(new BigDecimal("1150.00"));

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalArgumentException.class, () ->
                service.pay(booking, PaymentMethod.BANK_TRANSFER, null, null, null, "  ")
        );
        verifyNoInteractions(payments, bookings, tickets);
    }

    @Test
    void rejectsReVerifyingAnAlreadyRefundedPayment() {
        Payment payment = new Payment();
        payment.setStatus(com.skylanka.air.shared.entity.PaymentStatus.REFUNDED);

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalStateException.class, () ->
                service.updateStatus(payment, com.skylanka.air.shared.entity.PaymentStatus.VERIFIED)
        );
        verifyNoInteractions(payments, bookings);
    }

    @Test
    void rejectsRefundingAPaymentThatWasNeverCollected() {
        Payment payment = new Payment();
        payment.setStatus(com.skylanka.air.shared.entity.PaymentStatus.PENDING);

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(IllegalStateException.class, () ->
                service.updateStatus(payment, com.skylanka.air.shared.entity.PaymentStatus.REFUNDED)
        );
        verifyNoInteractions(payments, bookings);
    }

    @Test
    void declinedCardIsRecordedAsFailedAndBookingStaysPendingForRetry() {
        Booking booking = mock(Booking.class);
        com.skylanka.air.flight.entity.Flight flight = mock(com.skylanka.air.flight.entity.Flight.class);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getId()).thenReturn(11L);
        when(booking.getTotalFare()).thenReturn(new BigDecimal("1150.00"));
        when(booking.getFlight()).thenReturn(flight);
        when(flight.getStatus()).thenReturn(com.skylanka.air.shared.entity.FlightStatus.ON_TIME);
        when(flight.getDepartureDate()).thenReturn(java.time.LocalDate.now().plusDays(1));
        when(flight.getDepartureTime()).thenReturn(java.time.LocalTime.NOON);
        when(payments.findByBookingId(11L)).thenReturn(Optional.empty());

        PaymentService service = new PaymentService(
                payments, bookings, seats, tickets,
                new PaymentGatewayFactory(new MockPaymentGateway("mock"), new BankTransferGateway()),
                new DomainEventPublisher()
        );

        assertThrows(PaymentDeclinedException.class,
                () -> service.pay(booking, PaymentMethod.VISA, "4000000000000002", "12/30", "123", null));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertEquals(com.skylanka.air.shared.entity.PaymentStatus.FAILED, saved.getValue().getStatus());
        assertTrue(saved.getValue().getTransactionId().startsWith("FAILED-TX-"));
        verify(booking, never()).setStatus(BookingStatus.CONFIRMED);
        verify(tickets, never()).issue(any());
    }
}
