package com.skylanka.air.flight.controller;

import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.booking.service.BookingService;
import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.flight.repository.FlightRepository;
import com.skylanka.air.flight.service.FlightService;
import com.skylanka.air.seat.entity.Seat;
import com.skylanka.air.seat.service.SeatService;
import com.skylanka.air.shared.entity.Aircraft;
import com.skylanka.air.shared.entity.AircraftStatus;
import com.skylanka.air.shared.entity.FlightStatus;
import com.skylanka.air.shared.repository.AircraftRepository;
import com.skylanka.air.shared.repository.RouteRepository;
import com.skylanka.air.shared.security.AuthorizationSupport;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
public class FlightController {
    private final FlightRepository repo;
    private final FlightService service;
    private final SeatService seats;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final RouteRepository routes;
    private final AircraftRepository aircraftRepo;
    private final AuthorizationSupport authz;
    private com.skylanka.air.user.repository.UserRepository users;
    private com.skylanka.air.shared.service.AuditService auditService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setUserRepository(com.skylanka.air.user.repository.UserRepository users) {
        this.users = users;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setAuditService(com.skylanka.air.shared.service.AuditService auditService) {
        this.auditService = auditService;
    }

    public FlightController(
            FlightRepository r,
            FlightService s,
            SeatService ss,
            BookingRepository bookings,
            BookingService bookingService,
            RouteRepository routes,
            AircraftRepository aircraftRepo,
            AuthorizationSupport authz) {
        repo = r;
        service = s;
        seats = ss;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.routes = routes;
        this.aircraftRepo = aircraftRepo;
        this.authz = authz;
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String origin, @RequestParam(required = false) String destination, @RequestParam(required = false) String date,
                         @RequestParam(required = false) String status, @RequestParam(required = false) String scope, Model m) {
        // A malformed date used to fail request binding with an error page; now it is reported on the form.
        LocalDate parsedDate = null;
        String searchError = null;
        if (date != null && !date.isBlank()) {
            try {
                parsedDate = LocalDate.parse(date.trim());
            } catch (java.time.format.DateTimeParseException ex) {
                searchError = "Enter a valid departure date.";
            }
        }
        boolean sameCity = origin != null && destination != null
                && !origin.isBlank() && !destination.isBlank()
                && origin.trim().equalsIgnoreCase(destination.trim());
        if (searchError == null && sameCity) {
            searchError = "Origin and destination must be different.";
        }

        // Staff use this page as a lookup tool (status, load, aircraft), not for booking, so past dates are allowed.
        if (authz.hasAnyRole("OPERATIONS", "SUPPORT", "FINANCE", "MARKETING", "ADMIN")) {
            m.addAttribute("searchError", searchError);
            return staffSearch(origin, destination, searchError == null ? parsedDate : null, status, scope, m);
        }

        if (searchError == null && parsedDate != null && parsedDate.isBefore(LocalDate.now())) {
            searchError = "Departure date can't be in the past. Choose today or a later date.";
        }

        m.addAttribute("origin", origin);
        m.addAttribute("destination", destination);
        m.addAttribute("date", searchError == null ? parsedDate : null);
        m.addAttribute("searchError", searchError);
        var results = searchError == null
                ? service.search(origin, destination, parsedDate)
                : java.util.List.<Flight>of();
        m.addAttribute("results", results);
        if (searchError == null && results.isEmpty()) {
            m.addAttribute("hints", service.hints(origin, destination, parsedDate));
        }
        m.addAttribute("origins", service.origins());
        m.addAttribute("originDestinations", service.originDestinationMap());
        return "search";
    }

    private String staffSearch(String origin, String destination, LocalDate date, String status, String scope, Model m) {
        LocalDate today = LocalDate.now();
        boolean includePast = "all".equalsIgnoreCase(scope);

        FlightStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = FlightStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // unknown status value: treat as "any"
            }
        }
        final FlightStatus wantedStatus = statusFilter;

        var allFlights = repo.findAll();
        var results = allFlights.stream()
                .filter(f -> origin == null || origin.isBlank() || f.getOrigin().equalsIgnoreCase(origin.trim()))
                .filter(f -> destination == null || destination.isBlank() || f.getDestination().equalsIgnoreCase(destination.trim()))
                .filter(f -> date == null || date.equals(f.getDepartureDate()))
                .filter(f -> wantedStatus == null || f.getStatus() == wantedStatus)
                .filter(f -> includePast || date != null || !f.getDepartureDate().isBefore(today))
                .sorted(java.util.Comparator.comparing(Flight::getDepartureDate)
                        .thenComparing(Flight::getDepartureTime))
                .toList();

        java.util.Map<Long, Long> bookedMap = new java.util.HashMap<>();
        for (Flight f : results) {
            bookedMap.put(f.getId(), seats.forFlight(f.getId()).stream()
                    .filter(s -> s.getStatus() != com.skylanka.air.shared.entity.SeatStatus.AVAILABLE)
                    .count());
        }

        double avgLoad = results.stream()
                .filter(f -> f.getStatus() != FlightStatus.CANCELLED)
                .filter(f -> f.getSeatCapacity() != null && f.getSeatCapacity() > 0)
                .mapToDouble(f -> bookedMap.getOrDefault(f.getId(), 0L) * 100.0 / f.getSeatCapacity())
                .average().orElse(0);

        java.util.Map<String, java.util.List<String>> originDestinations = new java.util.TreeMap<>();
        for (Flight f : allFlights) {
            var list = originDestinations.computeIfAbsent(f.getOrigin(), k -> new java.util.ArrayList<>());
            if (!list.contains(f.getDestination())) list.add(f.getDestination());
        }

        m.addAttribute("origin", origin);
        m.addAttribute("destination", destination);
        m.addAttribute("date", date);
        m.addAttribute("status", wantedStatus == null ? "" : wantedStatus.name());
        m.addAttribute("scope", includePast ? "all" : "upcoming");
        m.addAttribute("statuses", FlightStatus.values());
        m.addAttribute("results", results);
        m.addAttribute("bookedMap", bookedMap);
        m.addAttribute("origins", originDestinations.keySet());
        m.addAttribute("originDestinations", originDestinations);
        m.addAttribute("resultCount", results.size());
        m.addAttribute("avgLoad", Math.round(avgLoad));
        m.addAttribute("delayedCount", results.stream().filter(f -> f.getStatus() == FlightStatus.DELAYED).count());
        m.addAttribute("cancelledCount", results.stream().filter(f -> f.getStatus() == FlightStatus.CANCELLED).count());
        return "staff-search";
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @GetMapping("/operations")
    public String operations(Model m) {
        var allFlights = repo.findAll();
        m.addAttribute("flights", allFlights);
        m.addAttribute("statuses", FlightStatus.values());
        java.util.Map<Long, java.util.List<Seat>> seatMap = new java.util.HashMap<>();
        for (Flight f : allFlights) {
            seatMap.put(f.getId(), seats.forFlight(f.getId()));
        }
        m.addAttribute("seatMap", seatMap);
        m.addAttribute("officersByFlight", service.officersByFlight());
        m.addAttribute("officerPool", users.findByRole(com.skylanka.air.shared.entity.Role.OPERATIONS).stream()
                .filter(com.skylanka.air.user.entity.User::isActive).toList());
        m.addAttribute("routeCatalog", routes.findAll());
        m.addAttribute("fleetCatalog", aircraftRepo.findAll());

        LocalDate today = LocalDate.now();

        var activeFlights = allFlights.stream()
                .filter(f -> f.getStatus() != FlightStatus.CANCELLED)
                .toList();

        long flightsToday = allFlights.stream()
                .filter(f -> today.equals(f.getDepartureDate()))
                .count();

        long delayedOrCancelledToday = allFlights.stream()
                .filter(f -> today.equals(f.getDepartureDate()))
                .filter(f -> f.getStatus() == FlightStatus.DELAYED || f.getStatus() == FlightStatus.CANCELLED)
                .count();

        java.util.List<Flight> avgLoadPool = activeFlights.stream()
                .filter(f -> f.getSeatCapacity() != null && f.getSeatCapacity() > 0)
                .toList();

        double avgLoadFactor = avgLoadPool.isEmpty() ? 0 : avgLoadPool.stream()
                .mapToDouble(f -> bookedSeatCount(seatMap, f) * 100.0 / f.getSeatCapacity())
                .average()
                .orElse(0);
        m.addAttribute("avgLoadFactor", Math.round(avgLoadFactor));

        java.util.List<Flight> attentionFlights = activeFlights.stream()
                .filter(f -> !today.isAfter(f.getDepartureDate()))
                .filter(f -> f.getAircraft() == null || f.getAircraft().isBlank()
                        || bookedSeatCount(seatMap, f) == 0
                        || (f.getSeatCapacity() != null && f.getSeatCapacity() > 0
                                && bookedSeatCount(seatMap, f) * 100.0 / f.getSeatCapacity() < 20))
                .toList();
        m.addAttribute("attentionFlights", attentionFlights);

        m.addAttribute("flightsToday", flightsToday);
        m.addAttribute("delayedOrCancelledToday", delayedOrCancelledToday);

        java.util.Map<String, Long> aircraftStatusCounts = new java.util.LinkedHashMap<>();
        for (AircraftStatus s : AircraftStatus.values()) {
            aircraftStatusCounts.put(s.name(), 0L);
        }
        for (var a : aircraftRepo.findAll()) {
            aircraftStatusCounts.merge(a.getStatus().name(), 1L, Long::sum);
        }
        m.addAttribute("aircraftStatusCounts", aircraftStatusCounts);

        return "operations";
    }

    private long bookedSeatCount(java.util.Map<Long, java.util.List<Seat>> seatMap, Flight f) {
        var flightSeats = seatMap.get(f.getId());
        if (flightSeats == null) return 0;
        return flightSeats.stream()
                .filter(s -> s.getStatus() != com.skylanka.air.shared.entity.SeatStatus.AVAILABLE)
                .count();
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/flight")
    public String create(@Valid @ModelAttribute Flight f, BindingResult br) {
        if (br.hasErrors()) return "redirect:/operations?error=validation";
        if (f.getDepartureDate().isBefore(LocalDate.now())
                || (f.getDepartureDate().isEqual(LocalDate.now())
                && f.getDepartureTime().isBefore(java.time.LocalTime.now()))) {
            return "redirect:/operations?error=date";
        }
        if (f.getOrigin().trim().equalsIgnoreCase(f.getDestination().trim())) {
            return "redirect:/operations?error=route";
        }
        Flight saved = service.save(f);
        // Use the fleet aircraft's cabin layout when the aircraft field names one (tail number or model).
        Aircraft layout = resolveAircraft(saved.getAircraft());
        if (layout != null) {
            seats.createSeats(saved, layout.getSeatsPerRow(), layout.getBusinessSeats());
        } else {
            seats.createSeats(saved);
        }
        // The officer who created the flight is responsible for it by default.
        Long creator = authz.currentUserId();
        if (creator != null) service.assignOfficer(saved.getId(), creator);
        return "redirect:/operations";
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/flight/{id}/officers")
    public String assignOfficer(@PathVariable Long id, @RequestParam Long userId, HttpSession s) {
        if (service.assignOfficer(id, userId)) {
            auditService.log(s, "ASSIGN_FLIGHT_OFFICER", String.valueOf(id), "Officer " + userId + " assigned.");
            return "redirect:/operations";
        }
        return "redirect:/operations?error=officer";
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/flight/{id}/officers/{userId}/remove")
    public String removeOfficer(@PathVariable Long id, @PathVariable Long userId, HttpSession s) {
        if (service.unassignOfficer(id, userId)) {
            auditService.log(s, "UNASSIGN_FLIGHT_OFFICER", String.valueOf(id), "Officer " + userId + " removed.");
        }
        return "redirect:/operations";
    }

    private Aircraft resolveAircraft(String label) {
        if (label == null || label.isBlank()) return null;
        String key = label.trim();
        return aircraftRepo.findByTailNumber(key.toUpperCase())
                .or(() -> aircraftRepo.findFirstByModelIgnoreCase(key))
                .orElse(null);
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/flight/{id}/status")
    public String status(@PathVariable Long id, @RequestParam FlightStatus status,
                          @RequestParam(required = false) Integer delayMinutes) {
        Flight f = repo.findById(id).orElseThrow();
        f.setStatus(status);
        f.setDelayMinutes(status == FlightStatus.DELAYED ? delayMinutes : null);
        service.save(f);
        if (status == FlightStatus.CANCELLED) {
            bookings.findByFlightId(id).stream()
                    .filter(b -> b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.PENDING
                            || b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.CONFIRMED)
                    .forEach(bookingService::cancelForFlight);
        } else if (status == FlightStatus.DELAYED && delayMinutes != null && delayMinutes > 0) {
            bookingService.notifyFlightDelay(f);
        }
        return "redirect:/operations";
    }
}
