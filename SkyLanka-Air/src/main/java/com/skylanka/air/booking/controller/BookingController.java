package com.skylanka.air.booking.controller;

import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.booking.service.BookingService;
import com.skylanka.air.flight.repository.FlightRepository;
import com.skylanka.air.flight.service.FlightService;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.payment.repository.RefundRequestRepository;
import com.skylanka.air.payment.entity.Payment;
import com.skylanka.air.seat.service.SeatService;
import com.skylanka.air.shared.repository.ComplaintRepository;
import com.skylanka.air.shared.security.AuthorizationSupport;
import com.skylanka.air.ticket.repository.TicketRepository;
import com.skylanka.air.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;

@Controller
public class BookingController {

    private final BookingRepository bookings;
    private final BookingService service;
    private final FlightRepository flights;
    private final FlightService flightService;
    private final SeatService seats;
    private final UserRepository users;
    private final PaymentRepository payments;
    private final TicketRepository tickets;
    private final ComplaintRepository complaints;
    private final AuthorizationSupport authz;
    private final RefundRequestRepository refundRequests;
    private final com.skylanka.air.user.service.UserService guestService;

    public BookingController(
            BookingRepository b, BookingService s, FlightRepository f, FlightService fs, SeatService ss,
            UserRepository u, PaymentRepository p, TicketRepository t, ComplaintRepository c,
            AuthorizationSupport authz, RefundRequestRepository rr,
            com.skylanka.air.user.service.UserService guestService) {

        bookings = b;
        service = s;
        flights = f;
        flightService = fs;
        seats = ss;
        users = u;
        payments = p;
        tickets = t;
        complaints = c;
        this.authz = authz;
        refundRequests = rr;
        this.guestService = guestService;
    }

    @GetMapping("/")
    public String home(Model m) {
        m.addAttribute("featured", flights.findFeaturedFlights());
        m.addAttribute("origins", flightService.origins());
        m.addAttribute("originDestinations", flightService.originDestinationMap());

        var activeRoutes = flights.findDistinctRoutes(
                List.of(com.skylanka.air.shared.entity.FlightStatus.CANCELLED,
                        com.skylanka.air.shared.entity.FlightStatus.COMPLETED));
        long destinationsServed = activeRoutes.stream()
                .map(FlightRepository.RoutePair::getDestination)
                .distinct()
                .count();

        m.addAttribute("destinationsServed", destinationsServed);
        m.addAttribute("routesOffered", activeRoutes.size());
        m.addAttribute("travelersServed", users.countByRole(com.skylanka.air.shared.entity.Role.CUSTOMER));

        return "home";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model m) {

        Long id = authz.currentUserId();
        if (id == null) return "redirect:/login";

        if (!authz.hasAnyRole("CUSTOMER")) {
            if (authz.hasAnyRole("ADMIN")) return "redirect:/admin";
            if (authz.hasAnyRole("OPERATIONS")) return "redirect:/operations";
            if (authz.hasAnyRole("SUPPORT")) return "redirect:/support";
            if (authz.hasAnyRole("FINANCE")) return "redirect:/finance";
            if (authz.hasAnyRole("MARKETING")) return "redirect:/marketing";
        }

        m.addAttribute("user", users.findById(id).orElseThrow());

        var customerBookings =
                bookings.findByCustomerIdOrderByCreatedAtDesc(id);

        m.addAttribute("bookings", customerBookings);
        var upcomingBookings = bookings.findUpcomingBookings(
                        id,
                        java.util.List.of(
                                com.skylanka.air.shared.entity.BookingStatus.PENDING,
                                com.skylanka.air.shared.entity.BookingStatus.CONFIRMED
                        ),
                        LocalDate.now(),
                        LocalTime.now()
                );
        m.addAttribute("upcoming", upcomingBookings);

        java.util.Map<Long, Payment> paymentMap = new java.util.HashMap<>();

        for (var b : customerBookings) {

            paymentMap.put(
                    b.getId(),
                    payments.findByBookingId(b.getId()).orElse(null)
            );
        }

        m.addAttribute("paymentMap", paymentMap);

        m.addAttribute("complaints", complaints.findByCustomerIdOrderByCreatedAtDesc(id));

        m.addAttribute("upcomingCount", upcomingBookings.size());
        m.addAttribute("confirmedCount", customerBookings.stream()
                .filter(b -> b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.CONFIRMED)
                .count());
        m.addAttribute("pendingCount", customerBookings.stream()
                .filter(b -> b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.PENDING)
                .count());
        m.addAttribute("totalSpent", paymentMap.values().stream()
                .filter(java.util.Objects::nonNull)
                .filter(p -> p.getStatus() == com.skylanka.air.shared.entity.PaymentStatus.PAID
                        || p.getStatus() == com.skylanka.air.shared.entity.PaymentStatus.VERIFIED)
                .map(Payment::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return "dashboard";
    }

    /** Guests (not signed in) may book; signed-in staff may not. */
    private boolean staffBlocked() {
        return authz.currentUserId() != null && !authz.hasAnyRole("CUSTOMER");
    }

    private void populateBookPage(Long id, Model m, HttpSession session) {
        var flight = flights.findById(id).orElseThrow();
        m.addAttribute("flight", flight);
        var flightSeats = seats.forFlight(id);
        m.addAttribute("seats", flightSeats);
        m.addAttribute("seatRows", seats.groupByRow(flightSeats));
        m.addAttribute("guestCheckout", authz.currentUserId() == null);
        m.addAttribute("seatHoldMinutes", seats.holdMinutes());
        m.addAttribute("holder", com.skylanka.air.seat.controller.SeatController.holderFor(authz, session));
        if (authz.currentUserId() != null) {
            users.findById(authz.currentUserId()).ifPresent(u -> m.addAttribute("profilePassport", u.getPassportNumber()));
        }
    }

    @GetMapping("/book/{id}")
    public String book(@PathVariable Long id, Model m, HttpSession session) {
        if (staffBlocked()) return "redirect:/dashboard";
        populateBookPage(id, m, session);
        return "book";
    }

    @PostMapping("/book/{id}")
    public String create(@PathVariable Long id,
                          @RequestParam(required = false) List<Long> seatIds,
                          @RequestParam(required = false) List<String> names,
                          @RequestParam(required = false) List<String> passports,
                          @RequestParam(required = false) List<String> contacts,
                          @RequestParam(required = false) String promoCode,
                          @RequestParam(required = false) String guestName,
                          @RequestParam(required = false) String guestEmail,
                          @RequestParam(required = false) String guestContact,
                          Model m, HttpSession session,
                          HttpServletRequest request) {

        if (staffBlocked()) return "redirect:/dashboard";

        try {
            Long uid = authz.currentUserId();
            // Seats this browser held on the seat map (identity is taken BEFORE any guest sign-in).
            String holder = com.skylanka.air.seat.controller.SeatController.holderFor(authz, session);
            com.skylanka.air.user.entity.User guest = null;

            if (uid == null) {
                guest = createGuestAccount(guestName, guestEmail, guestContact);
                uid = guest.getId();
            }

            com.skylanka.air.booking.entity.Booking b;
            try {
                b = service.createGroup(uid, id, seatIds, names, passports, contacts, promoCode, holder);
            } catch (RuntimeException failure) {
                if (guest != null) users.delete(guest); // don't leave an orphan guest account behind
                throw failure;
            }

            if (guest != null) {
                request.changeSessionId(); // rotate the id when the guest becomes signed in (session fixation)
                session.setAttribute("userId", guest.getId());
                session.setAttribute("name", guest.getName());
                session.setAttribute("email", guest.getEmail());
                session.setAttribute("role", guest.getRole().name());
            }

            return "redirect:/booking/" + b.getReference();

        } catch (Exception e) {

            m.addAttribute("error", e.getMessage());
            populateBookPage(id, m, session);

            return "book";
        }
    }

    private static final java.util.regex.Pattern GUEST_EMAIL =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final java.util.regex.Pattern GUEST_CONTACT =
            java.util.regex.Pattern.compile("\\+?[0-9]{7,15}");

    private com.skylanka.air.user.entity.User createGuestAccount(String name, String email, String contact) {
        if (name == null || name.trim().length() < 2) {
            throw new IllegalArgumentException("Enter your name to continue as a guest.");
        }
        String e = email == null ? "" : email.trim().toLowerCase();
        if (!GUEST_EMAIL.matcher(e).matches()) {
            throw new IllegalArgumentException("Enter a valid email address so we can send your ticket.");
        }
        if (contact == null || !GUEST_CONTACT.matcher(contact.trim()).matches()) {
            throw new IllegalArgumentException("Enter a valid contact number (7-15 digits, optional leading +).");
        }
        if (users.findByEmail(e).isPresent()) {
            throw new IllegalArgumentException(
                    "That email already has an account. Please sign in to book, or use a different email.");
        }
        return guestService.createGuest(name, e, contact);
    }

    @GetMapping("/booking/{ref}")
    public String details(@PathVariable String ref, Model m) {

        var b = bookings.findByReference(ref).orElse(null);
        if (b == null) return "redirect:/dashboard";

        if (!authz.isOwnerOrHasAnyRole(b.getCustomer().getId(), "SUPPORT", "FINANCE", "ADMIN")) {
            return "redirect:/login";
        }

        m.addAttribute("booking", b);
        m.addAttribute("payment", payments.findByBookingId(b.getId()).orElse(null));
        m.addAttribute("tickets", tickets.findByBookingId(b.getId()));
        m.addAttribute("refundRequests", refundRequests.findByBookingIdOrderByCreatedAtDesc(b.getId()));
        m.addAttribute(
                "canManage",
                authz.hasAnyRole("CUSTOMER") && authz.isOwner(b.getCustomer().getId())
        );

        return "booking";
    }

    @GetMapping("/booking/{ref}/modify")
    public String modifyForm(@PathVariable String ref, Model m) {

        if (!authz.isAuthenticated()) return "redirect:/login";

        var b = bookings.findByReference(ref).orElseThrow();

        if (!authz.isOwnerOrHasAnyRole(b.getCustomer().getId(), "SUPPORT", "ADMIN")) {
            return "redirect:/dashboard";
        }

        if (b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.CANCELLED ||
                b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.COMPLETED ||
                hasDeparted(b)) {

            return "redirect:/booking/" + ref;
        }

        m.addAttribute("booking", b);

        return "modify-booking";
    }

    @PostMapping("/booking/{ref}/modify")
    public String modify(
            @PathVariable String ref,
            @RequestParam String passengerName,
            @RequestParam String passportNumber,
            @RequestParam String passengerContact,
            Model m) {

        if (!authz.isAuthenticated()) return "redirect:/login";

        var b = bookings.findByReference(ref).orElse(null);
        if (b == null) return "redirect:/dashboard";

        if (!authz.isOwnerOrHasAnyRole(b.getCustomer().getId(), "SUPPORT", "ADMIN")) {
            return "redirect:/dashboard";
        }

        try {

            service.modify(b, passengerName, passportNumber, passengerContact);
            return "redirect:/booking/" + ref;

        } catch (Exception e) {

            m.addAttribute("booking", b);
            m.addAttribute("error", e.getMessage());

            return "modify-booking";
        }
    }

    @PostMapping("/booking/{ref}/passenger/{passengerId}/modify")
    public String modifyPassenger(
            @PathVariable String ref,
            @PathVariable Long passengerId,
            @RequestParam String passengerName,
            @RequestParam String passportNumber,
            @RequestParam String passengerContact,
            Model m) {

        if (!authz.isAuthenticated()) return "redirect:/login";

        var b = bookings.findByReference(ref).orElse(null);
        if (b == null) return "redirect:/dashboard";

        if (!authz.isOwnerOrHasAnyRole(b.getCustomer().getId(), "SUPPORT", "ADMIN")) {
            return "redirect:/dashboard";
        }

        try {

            service.modifyPassenger(b, passengerId, passengerName, passportNumber, passengerContact);
            return "redirect:/booking/" + ref;

        } catch (Exception e) {

            m.addAttribute("booking", b);
            m.addAttribute("error", e.getMessage());

            return "modify-booking";
        }
    }

    @PostMapping("/booking/{ref}/cancel")
    public String cancel(@PathVariable String ref) {

        var b = bookings.findByReference(ref).orElse(null);
        if (b == null) return "redirect:/dashboard";

        boolean isOwner = authz.isOwner(b.getCustomer().getId());
        boolean isStaffOverride = authz.hasAnyRole("SUPPORT", "ADMIN");

        if (!isOwner && !isStaffOverride) {
            return "redirect:/dashboard";
        }

        String errorRedirect = isStaffOverride && !isOwner
                ? "redirect:/support?error=cancel"
                : "redirect:/dashboard?error=cancel";
        String successRedirect = isStaffOverride && !isOwner
                ? "redirect:/support"
                : "redirect:/dashboard";

        try {
            service.cancel(b);
        } catch (IllegalStateException e) {
            return errorRedirect;
        }

        return successRedirect;
    }

    @PostMapping("/complaints")
    public String complaint(@RequestParam String subject, @RequestParam String description, @RequestParam(required = false) String reference) {

        Long id = authz.currentUserId();
        if (id == null) return "redirect:/login";

        var c = new com.skylanka.air.shared.entity.Complaint();

        c.setCustomer(users.findById(id).orElseThrow());

        c.setSubject(subject);
        c.setDescription(description);

        if (reference != null && !reference.isBlank()) {
            var booking = bookings.findByReference(reference).orElse(null);
            if (booking != null && booking.getCustomer().getId().equals(id)) {
                c.setBooking(booking);
            }
        }

        complaints.save(c);

        return "redirect:/dashboard?complaint=" + c.getDisplayReference();
    }

    private boolean hasDeparted(com.skylanka.air.booking.entity.Booking booking) {
        return LocalDateTime.of(
                booking.getFlight().getDepartureDate(),
                booking.getFlight().getDepartureTime()
        ).isBefore(LocalDateTime.now());
    }
}
