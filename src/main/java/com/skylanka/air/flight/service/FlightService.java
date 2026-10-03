package com.skylanka.air.flight.service;

import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.flight.repository.FlightRepository;
import com.skylanka.air.shared.entity.FlightStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class FlightService {
    private final FlightRepository repo;

    public FlightService(FlightRepository r) {
        repo = r;
    }

    public List<Flight> search(String o, String d, LocalDate date) {
        return repo.search(
                blank(o),
                blank(d),
                date,
                List.of(FlightStatus.CANCELLED, FlightStatus.COMPLETED),
                LocalDate.now(),
                LocalTime.now()
        );
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Ideas shown when a customer search comes back empty. */
    public record SearchHints(List<LocalDate> otherDates, List<String> otherDestinations) {
        public boolean isEmpty() {
            return otherDates.isEmpty() && otherDestinations.isEmpty();
        }

        // Bean-style getters so Thymeleaf/SpEL can read the values as properties.
        public List<LocalDate> getOtherDates() {
            return otherDates;
        }

        public List<String> getOtherDestinations() {
            return otherDestinations;
        }
    }

    /**
     * Builds "try adjusting your search" suggestions: other days the same route operates (when a date was
     * chosen) and other destinations flown from the chosen origin.
     */
    public SearchHints hints(String o, String d, LocalDate date) {
        List<LocalDate> otherDates = new ArrayList<>();
        if (date != null) {
            for (Flight f : search(o, d, null)) {
                if (!otherDates.contains(f.getDepartureDate())) otherDates.add(f.getDepartureDate());
                if (otherDates.size() == 5) break;
            }
        }
        List<String> otherDestinations = new ArrayList<>();
        String origin = blank(o);
        String destination = blank(d);
        if (origin != null) {
            for (java.util.Map.Entry<String, List<String>> e : originDestinationMap().entrySet()) {
                if (!e.getKey().equalsIgnoreCase(origin)) continue;
                for (String dest : e.getValue()) {
                    if (destination == null || !dest.equalsIgnoreCase(destination)) otherDestinations.add(dest);
                }
            }
        }
        return new SearchHints(otherDates, otherDestinations);
    }

    public java.util.Map<String, List<String>> originDestinationMap() {
        List<FlightRepository.RoutePair> routes = repo.findDistinctRoutes(
                List.of(FlightStatus.CANCELLED, FlightStatus.COMPLETED)
        );
        java.util.Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        for (FlightRepository.RoutePair r : routes) {
            map.computeIfAbsent(r.getOrigin(), k -> new ArrayList<>()).add(r.getDestination());
        }
        return map;
    }

    public List<String> origins() {
        return new ArrayList<>(originDestinationMap().keySet());
    }

    private AirportService airportService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAirportService(AirportService airportService) {
        this.airportService = airportService;
    }

    private com.skylanka.air.user.repository.UserRepository userRepo;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setUserRepository(com.skylanka.air.user.repository.UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /** Assigns an operations officer to a flight. Returns false if either record is missing or the user is not an officer. */
    @org.springframework.transaction.annotation.Transactional
    public boolean assignOfficer(Long flightId, Long userId) {
        if (userRepo == null) return false;
        Flight f = repo.findById(flightId).orElse(null);
        var u = userRepo.findById(userId).orElse(null);
        if (f == null || u == null || !u.isActive()
                || u.getRole() != com.skylanka.air.shared.entity.Role.OPERATIONS) return false;
        boolean added = f.getOperationsOfficers().add(u);
        if (added) repo.save(f);
        return true;
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean unassignOfficer(Long flightId, Long userId) {
        Flight f = repo.findById(flightId).orElse(null);
        if (f == null) return false;
        boolean removed = f.getOperationsOfficers().removeIf(u -> u.getId().equals(userId));
        if (removed) repo.save(f);
        return removed;
    }

    /** Flight id -> assigned officers, loaded inside one transaction so the lazy collections resolve. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<Long, List<com.skylanka.air.user.entity.User>> officersByFlight() {
        java.util.Map<Long, List<com.skylanka.air.user.entity.User>> out = new java.util.HashMap<>();
        for (Flight f : repo.findAll()) {
            out.put(f.getId(), new ArrayList<>(f.getOperationsOfficers()));
        }
        return out;
    }

    public Flight save(Flight f) {
        if (airportService != null) airportService.link(f);
        f.setUpdatedAt(LocalDateTime.now());
        return repo.save(f);
    }
}
