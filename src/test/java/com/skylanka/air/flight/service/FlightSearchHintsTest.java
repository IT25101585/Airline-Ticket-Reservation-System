package com.skylanka.air.flight.service;

import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.flight.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightSearchHintsTest {

    @Mock FlightRepository repo;

    private Flight flightOn(LocalDate d) {
        Flight f = new Flight();
        f.setDepartureDate(d);
        return f;
    }

    private FlightRepository.RoutePair pair(String o, String d) {
        return new FlightRepository.RoutePair() {
            public String getOrigin() { return o; }
            public String getDestination() { return d; }
        };
    }

    @Test
    void suggestsOtherDatesOnTheSameRouteAndOtherDestinationsFromTheOrigin() {
        LocalDate chosen = LocalDate.now().plusDays(1);
        LocalDate other = LocalDate.now().plusDays(4);
        // First repository call is the date-less route search, second is the route catalogue.
        when(repo.search(any(), any(), any(), any(), any(), any())).thenReturn(List.of(flightOn(other), flightOn(other)));
        when(repo.findDistinctRoutes(any())).thenReturn(List.of(
                pair("Colombo", "Dubai"), pair("Colombo", "Singapore"), pair("Male", "Dubai")));

        var hints = new FlightService(repo).hints("Colombo", "Dubai", chosen);

        assertEquals(List.of(other), hints.getOtherDates());
        assertEquals(List.of("Singapore"), hints.getOtherDestinations());
        assertFalse(hints.isEmpty());
    }

    @Test
    void noDateMeansNoDateSuggestions() {
        when(repo.findDistinctRoutes(any())).thenReturn(List.of(pair("Colombo", "Dubai")));

        var hints = new FlightService(repo).hints("Colombo", "Dubai", null);

        assertTrue(hints.getOtherDates().isEmpty());
        assertTrue(hints.isEmpty());
    }
}
