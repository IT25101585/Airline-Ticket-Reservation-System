package com.skylanka.air.flight.service;

import com.skylanka.air.flight.entity.Airport;
import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.flight.repository.AirportRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Keeps the Flight -> Airport associations (departureAirport / arrivalAirport) in step with the
 * free-text origin / destination used by search, and derives whether a flight is international.
 */
@Service
public class AirportService {
    private final AirportRepository airports;

    public AirportService(AirportRepository airports) {
        this.airports = airports;
    }

    public List<Airport> all() {
        return airports.findAll();
    }

    public Optional<Airport> resolve(String place) {
        if (place == null || place.isBlank()) return Optional.empty();
        String p = place.trim();
        Optional<Airport> byCity = airports.findFirstByCityIgnoreCase(p);
        if (byCity.isPresent()) return byCity;
        return airports.findByAirportCodeIgnoreCase(p);
    }

    /** Resolves both airports for the flight and sets {@code international}. Returns true if anything changed. */
    public boolean link(Flight f) {
        boolean changed = false;
        Airport dep = resolve(f.getOrigin()).orElse(null);
        Airport arr = resolve(f.getDestination()).orElse(null);
        if (dep != null && f.getDepartureAirport() != dep) {
            f.setDepartureAirport(dep);
            changed = true;
        }
        if (arr != null && f.getArrivalAirport() != arr) {
            f.setArrivalAirport(arr);
            changed = true;
        }
        if (dep != null && arr != null) {
            boolean intl = !dep.getCountry().equalsIgnoreCase(arr.getCountry());
            if (f.isInternational() != intl) {
                f.setInternational(intl);
                changed = true;
            }
        }
        return changed;
    }

    public void seedDefaults() {
        if (airports.count() > 0) return;
        airports.saveAll(List.of(
                new Airport("CMB", "Colombo", "Bandaranaike International Airport", "Sri Lanka"),
                new Airport("JAF", "Jaffna", "Jaffna International Airport", "Sri Lanka"),
                new Airport("HRI", "Hambantota", "Mattala Rajapaksa International Airport", "Sri Lanka"),
                new Airport("DXB", "Dubai", "Dubai International Airport", "United Arab Emirates"),
                new Airport("SIN", "Singapore", "Changi Airport", "Singapore"),
                new Airport("MLE", "Male", "Velana International Airport", "Maldives"),
                new Airport("DEL", "Delhi", "Indira Gandhi International Airport", "India"),
                new Airport("MAA", "Chennai", "Chennai International Airport", "India"),
                new Airport("BOM", "Mumbai", "Chhatrapati Shivaji Maharaj International Airport", "India"),
                new Airport("BKK", "Bangkok", "Suvarnabhumi Airport", "Thailand"),
                new Airport("KUL", "Kuala Lumpur", "Kuala Lumpur International Airport", "Malaysia"),
                new Airport("DOH", "Doha", "Hamad International Airport", "Qatar"),
                new Airport("LHR", "London", "Heathrow Airport", "United Kingdom")
        ));
    }
}
