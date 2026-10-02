package com.skylanka.air.seat.service;

import com.skylanka.air.seat.entity.Seat;
import com.skylanka.air.seat.repository.SeatRepository;
import com.skylanka.air.shared.entity.SeatStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceHoldTest {

    @Mock SeatRepository seats;

    private Seat availableSeat() {
        Seat s = new Seat();
        s.setSeatNumber("7C");
        return s; // status defaults to AVAILABLE
    }

    @Test
    void refusesAHoldOnceTheVisitorAlreadyHoldsTheMaximum() {
        Seat seat = availableSeat();
        when(seats.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));
        when(seats.countByStatusAndHeldByAndHeldAtAfter(eq(SeatStatus.HELD), eq("S1"), any(LocalDateTime.class)))
                .thenReturn((long) SeatService.MAX_SEATS_PER_HOLDER);

        var result = new SeatService(seats).hold(1L, "S1");

        assertFalse(result.held());
        assertTrue(result.limitReached());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        verify(seats, never()).save(any());
    }

    @Test
    void allowsAHoldBelowTheMaximum() {
        Seat seat = availableSeat();
        when(seats.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));
        when(seats.countByStatusAndHeldByAndHeldAtAfter(eq(SeatStatus.HELD), eq("S1"), any(LocalDateTime.class)))
                .thenReturn(SeatService.MAX_SEATS_PER_HOLDER - 1L);

        var result = new SeatService(seats).hold(1L, "S1");

        assertTrue(result.held());
        assertFalse(result.limitReached());
        assertEquals(SeatStatus.HELD, seat.getStatus());
        assertEquals("S1", seat.getHeldBy());
        verify(seats).save(seat);
    }

    @Test
    void refreshingAnExistingHoldIsNotBlockedByTheCap() {
        Seat seat = availableSeat();
        seat.setStatus(SeatStatus.HELD);
        seat.setHeldBy("S1");
        seat.setHeldAt(LocalDateTime.now());
        when(seats.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));

        var result = new SeatService(seats).hold(1L, "S1");

        assertTrue(result.held());
        verify(seats, never()).countByStatusAndHeldByAndHeldAtAfter(any(), any(), any());
    }
}
