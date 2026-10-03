package com.skylanka.air.booking.controller;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.entity.Passenger;
import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.booking.service.BookingService;
import com.skylanka.air.shared.security.AuthorizationSupport;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class ApiBookingController {

    private final BookingRepository bookings;
    private final BookingService service;
    private final AuthorizationSupport authz;

    public ApiBookingController(BookingRepository b, BookingService s, AuthorizationSupport authz) {
        bookings = b;
        service = s;
        this.authz = authz;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping
    public List<BookingSummary> mine() {
        return bookings.findByCustomerIdOrderByCreatedAtDesc(authz.currentUserId())
                .stream()
                .map(BookingSummary::of)
                .toList();
    }

    @GetMapping("/{ref}")
    public ResponseEntity<BookingDetail> get(@PathVariable String ref) {
        Booking b = bookings.findByReference(ref).orElse(null);
        if (b == null) return ResponseEntity.notFound().build();

        if (!authz.isOwnerOrHasAnyRole(b.getCustomer().getId(), "SUPPORT", "FINANCE", "ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(BookingDetail.of(b));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateBookingRequest req) {
        try {
            Booking b = service.createGroup(
                    authz.currentUserId(),
                    req.flightId(),
                    req.seatIds(),
                    req.names(),
                    req.passports(),
                    req.contacts(),
                    req.promoCode()
            );
            return ResponseEntity.ok(BookingDetail.of(b));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{ref}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String ref) {
        Booking b = bookings.findByReference(ref).orElse(null);
        if (b == null) return ResponseEntity.notFound().build();

        if (!authz.isOwnerOrHasAnyRole(b.getCustomer().getId(), "SUPPORT", "ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        try {
            service.cancel(b);
            return ResponseEntity.ok(Map.of("status", "CANCELLED"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    public record PassengerView(Long id, String name, String seat) {
        static PassengerView of(Passenger p) {
            return new PassengerView(
                    p.getId(),
                    p.getName(),
                    p.getSeat() != null ? p.getSeat().getSeatNumber() : null
            );
        }
    }

    public record BookingSummary(String reference, String flightNumber, String status, BigDecimal totalFare) {
        static BookingSummary of(Booking b) {
            return new BookingSummary(
                    b.getReference(),
                    b.getFlight().getFlightNumber(),
                    b.getStatus().name(),
                    b.getTotalFare()
            );
        }
    }

    public record BookingDetail(
            String reference,
            String flightNumber,
            String origin,
            String destination,
            LocalDate departureDate,
            LocalTime departureTime,
            String status,
            BigDecimal totalFare,
            List<PassengerView> passengers) {

        static BookingDetail of(Booking b) {
            return new BookingDetail(
                    b.getReference(),
                    b.getFlight().getFlightNumber(),
                    b.getFlight().getOrigin(),
                    b.getFlight().getDestination(),
                    b.getFlight().getDepartureDate(),
                    b.getFlight().getDepartureTime(),
                    b.getStatus().name(),
                    b.getTotalFare(),
                    b.getPassengers().stream().map(PassengerView::of).toList()
            );
        }
    }

    public record CreateBookingRequest(
            Long flightId,
            List<Long> seatIds,
            List<String> names,
            List<String> passports,
            List<String> contacts,
            String promoCode) {
    }
}
