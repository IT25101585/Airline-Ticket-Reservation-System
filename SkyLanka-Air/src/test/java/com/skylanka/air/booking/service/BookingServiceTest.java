package com.skylanka.air.booking.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.flight.repository.FlightRepository;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.payment.service.RefundPolicyService;
import com.skylanka.air.seat.entity.Seat;
import com.skylanka.air.seat.repository.SeatRepository;
import com.skylanka.air.shared.entity.FlightStatus;
import com.skylanka.air.shared.entity.SeatStatus;
import com.skylanka.air.shared.event.DomainEventPublisher;
import com.skylanka.air.shared.event.InAppNotificationObserver;
import com.skylanka.air.shared.repository.NotificationRepository;
import com.skylanka.air.shared.repository.PromotionRepository;
import com.skylanka.air.ticket.service.TicketService;
import com.skylanka.air.user.entity.User;
import com.skylanka.air.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookings;
    @Mock UserRepository users;
    @Mock FlightRepository flights;
    @Mock SeatRepository seats;
    @Mock PromotionRepository promotions;
    @Mock NotificationRepository notifications;
    @Mock PaymentRepository payments;
    @Mock TicketService tickets;

    /** Real publisher with the real in-app observer, backed by the mocked repository. */
    private DomainEventPublisher events() {
        DomainEventPublisher publisher = new DomainEventPublisher();
        publisher.register(new InAppNotificationObserver(notifications));
        return publisher;
    }

    @Test
    void createsBookingAndLocksSeatWithCalculatedFare() {
        User user = new User();
        Flight flight = mock(Flight.class);
        Seat seat = mock(Seat.class);
        Booking saved = new Booking();

        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(flights.findById(2L)).thenReturn(Optional.of(flight));
        when(flight.getId()).thenReturn(2L);
        when(flight.getStatus()).thenReturn(FlightStatus.ON_TIME);
        when(flight.getDepartureDate()).thenReturn(LocalDate.now().plusDays(1));
        when(flight.getDepartureTime()).thenReturn(LocalTime.of(10, 0));
        when(flight.getBaseFare()).thenReturn(new BigDecimal("1000.00"));
        when(seats.findByIdForUpdate(3L)).thenReturn(Optional.of(seat));
        when(seat.getFlight()).thenReturn(flight);
        when(seat.getStatus()).thenReturn(SeatStatus.AVAILABLE);
        when(seat.getSeatClass()).thenReturn("ECONOMY");
        when(bookings.findByReference(anyString())).thenReturn(Optional.empty());
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setReference(saved.getReference());
            return booking;
        });

        BookingService service = new BookingService(
                bookings, users, flights, seats, promotions, payments, tickets,
                events(), new RefundPolicyService()
        );

        Booking result = service.create(
                1L, 2L, 3L, "Passenger", "P123", "0770000000", null
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("1150.00"), result.getTotalFare());
        verify(seat).setStatus(SeatStatus.BOOKED);
        verify(seats).save(seat);
        verify(bookings).save(any(Booking.class));
        verify(notifications).save(any());
    }

    @Test
    void refusesBookingOnCompletedFlight() {
        Flight flight = mock(Flight.class);
        Seat seat = mock(Seat.class);
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        when(flights.findById(2L)).thenReturn(Optional.of(flight));
        when(flight.getId()).thenReturn(2L);
        when(flight.getStatus()).thenReturn(FlightStatus.COMPLETED);
        when(seats.findByIdForUpdate(3L)).thenReturn(Optional.of(seat));
        when(seat.getFlight()).thenReturn(flight);
        when(seat.getStatus()).thenReturn(SeatStatus.AVAILABLE);

        BookingService service = new BookingService(
                bookings, users, flights, seats, promotions, payments, tickets,
                events(), new RefundPolicyService()
        );

        assertThrows(IllegalStateException.class, () ->
                service.create(1L, 2L, 3L, "Passenger", "P123", "0770000000", null)
        );
        verifyNoInteractions(bookings, notifications);
    }
}