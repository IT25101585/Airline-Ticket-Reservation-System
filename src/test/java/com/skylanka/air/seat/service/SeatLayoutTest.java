package com.skylanka.air.seat.service;

import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.seat.entity.Seat;
import com.skylanka.air.seat.repository.SeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatLayoutTest {

    @Mock SeatRepository seats;

    private List<Seat> generate(int capacity, int perRow, int business) {
        Flight f = new Flight();
        f.setSeatCapacity(capacity);
        new SeatService(seats).createSeats(f, perRow, business);
        ArgumentCaptor<Seat> captor = ArgumentCaptor.forClass(Seat.class);
        verify(seats, times(capacity)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void defaultLayoutKeepsSixAbreastWithSixBusinessSeats() {
        Flight f = new Flight();
        f.setSeatCapacity(12);
        new SeatService(seats).createSeats(f);
        ArgumentCaptor<Seat> captor = ArgumentCaptor.forClass(Seat.class);
        verify(seats, times(12)).save(captor.capture());
        List<Seat> all = captor.getAllValues();

        assertEquals("1A", all.get(0).getSeatNumber());
        assertEquals("1F", all.get(5).getSeatNumber());
        assertEquals("2A", all.get(6).getSeatNumber());
        assertEquals("BUSINESS", all.get(5).getSeatClass());
        assertEquals("ECONOMY", all.get(6).getSeatClass());
    }

    @Test
    void customLayoutUsesTheAircraftSeatsPerRowAndBusinessCount() {
        List<Seat> all = generate(10, 4, 4);

        assertEquals("1D", all.get(3).getSeatNumber());
        assertEquals("2A", all.get(4).getSeatNumber());
        assertEquals("BUSINESS", all.get(3).getSeatClass());
        assertEquals("ECONOMY", all.get(4).getSeatClass());
    }

    @Test
    void businessSeatsAreCappedAtCapacity() {
        List<Seat> all = generate(3, 6, 20);
        assertTrue(all.stream().allMatch(s -> "BUSINESS".equals(s.getSeatClass())));
    }
}
