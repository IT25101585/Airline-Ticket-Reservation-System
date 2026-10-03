package com.skylanka.air.shared.controller;

import com.skylanka.air.shared.entity.Aircraft;
import com.skylanka.air.shared.entity.AircraftStatus;
import com.skylanka.air.shared.entity.Route;
import com.skylanka.air.shared.repository.AircraftRepository;
import com.skylanka.air.shared.repository.RouteRepository;
import com.skylanka.air.shared.service.AuditService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class FleetController {

    private final AircraftRepository aircraft;
    private final RouteRepository routes;
    private final AuditService auditService;

    public FleetController(AircraftRepository a, RouteRepository r, AuditService audit) {
        aircraft = a;
        routes = r;
        auditService = audit;
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @GetMapping("/operations/fleet")
    public String fleet(Model m) {
        m.addAttribute("aircraft", aircraft.findAll());
        m.addAttribute("routes", routes.findAll());
        m.addAttribute("statuses", AircraftStatus.values());
        return "fleet";
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/fleet/aircraft")
    public String addAircraft(
            @RequestParam String tailNumber,
            @RequestParam String model,
            @RequestParam int seatCapacity,
            @RequestParam(required = false) Integer seatsPerRow,
            @RequestParam(required = false) Integer businessSeats,
            HttpSession s) {

        String normalized = tailNumber == null ? null : tailNumber.trim().toUpperCase();
        if (normalized == null || normalized.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("Tail number and model are required.");
        }
        if (seatCapacity <= 0) {
            throw new IllegalArgumentException("Seat capacity must be positive.");
        }
        if (aircraft.findByTailNumber(normalized).isPresent()) {
            throw new IllegalArgumentException("An aircraft with that tail number already exists.");
        }

        Aircraft a = new Aircraft();
        a.setTailNumber(normalized);
        a.setModel(model.trim());
        a.setSeatCapacity(seatCapacity);
        if (seatsPerRow != null || businessSeats != null) {
            int perRow = seatsPerRow == null ? Aircraft.DEFAULT_SEATS_PER_ROW : seatsPerRow;
            int business = businessSeats == null ? Math.min(Aircraft.DEFAULT_BUSINESS_SEATS, seatCapacity) : businessSeats;
            if (!validLayout(seatCapacity, perRow, business)) {
                throw new IllegalArgumentException("Seats per row must be between " + Aircraft.MIN_SEATS_PER_ROW
                        + " and " + Aircraft.MAX_SEATS_PER_ROW + ", and business seats cannot exceed the capacity.");
            }
            a.setSeatsPerRow(perRow);
            a.setBusinessSeats(business);
        }
        aircraft.save(a);

        auditService.log(s, "ADD_AIRCRAFT", normalized, model.trim() + " added to the fleet.");

        return "redirect:/operations/fleet";
    }

    /**
     * Changes an aircraft's cabin layout (capacity, seats per row, business seats). Only flights created
     * afterwards use it; seat maps of existing flights, and any bookings on them, are left untouched.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/fleet/aircraft/{id}/layout")
    public String updateAircraftLayout(
            @PathVariable Long id,
            @RequestParam int seatCapacity,
            @RequestParam int seatsPerRow,
            @RequestParam int businessSeats,
            HttpSession s) {

        Aircraft a = aircraft.findById(id).orElseThrow();
        if (!validLayout(seatCapacity, seatsPerRow, businessSeats)) {
            return "redirect:/operations/fleet?error=layout";
        }
        a.setSeatCapacity(seatCapacity);
        a.setSeatsPerRow(seatsPerRow);
        a.setBusinessSeats(businessSeats);
        aircraft.save(a);

        auditService.log(s, "UPDATE_AIRCRAFT_LAYOUT", a.getTailNumber(),
                "Layout set to " + seatCapacity + " seats, " + seatsPerRow + " per row, "
                        + businessSeats + " business.");

        return "redirect:/operations/fleet?layoutSaved";
    }

    private static boolean validLayout(int capacity, int seatsPerRow, int businessSeats) {
        return capacity > 0
                && seatsPerRow >= Aircraft.MIN_SEATS_PER_ROW
                && seatsPerRow <= Aircraft.MAX_SEATS_PER_ROW
                && businessSeats >= 0
                && businessSeats <= capacity;
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/fleet/aircraft/{id}/status")
    public String updateAircraftStatus(
            @PathVariable Long id,
            @RequestParam AircraftStatus status,
            HttpSession s) {

        Aircraft a = aircraft.findById(id).orElseThrow();
        a.setStatus(status);
        aircraft.save(a);

        auditService.log(s, "UPDATE_AIRCRAFT_STATUS", a.getTailNumber(), "Status set to " + status + ".");

        return "redirect:/operations/fleet";
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/fleet/route")
    public String addRoute(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false) Integer distanceKm,
            @RequestParam(required = false) Integer standardDurationMinutes,
            HttpSession s) {

        if (origin == null || origin.isBlank() || destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Origin and destination are required.");
        }
        if (origin.trim().equalsIgnoreCase(destination.trim())) {
            throw new IllegalArgumentException("Origin and destination cannot be the same.");
        }

        Route r = new Route();
        r.setOrigin(origin.trim());
        r.setDestination(destination.trim());
        r.setDistanceKm(distanceKm);
        r.setStandardDurationMinutes(standardDurationMinutes);
        routes.save(r);

        auditService.log(
                s,
                "ADD_ROUTE",
                origin.trim() + " → " + destination.trim(),
                "Route added to the catalog."
        );

        return "redirect:/operations/fleet";
    }
}
