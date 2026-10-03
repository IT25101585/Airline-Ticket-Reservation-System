package com.skylanka.air.shared.controller;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.entity.Passenger;
import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.booking.repository.PassengerRepository;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.payment.repository.RefundRequestRepository;
import com.skylanka.air.flight.repository.FlightRepository;
import com.skylanka.air.ticket.repository.TicketRepository;
import com.skylanka.air.ticket.service.TicketService;
import com.skylanka.air.shared.entity.*;
import com.skylanka.air.shared.repository.*;
import com.skylanka.air.shared.service.AuditService;
import com.skylanka.air.seat.repository.SeatRepository;
import com.skylanka.air.user.repository.UserRepository;
import com.skylanka.air.user.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class OperationsController {

    private final BookingRepository bookings;
    private final CheckInRepository checkins;
    private final ComplaintRepository complaints;
    private final PromotionRepository promotions;
    private final UserRepository users;
    private final AuditLogRepository audits;
    private final SeatRepository seats;
    private final PaymentRepository payments;
    private final FlightRepository flights;
    private final TicketRepository tickets;
    private final AuditService auditService;
    private final NotificationRepository notificationRepository;
    private final PassengerRepository passengers;
    private final com.skylanka.air.seat.service.SeatService seatService;
    private final CampaignRepository campaigns;
    private final RefundRequestRepository refundRequests;
    private final TicketService ticketService;

    public OperationsController(
            BookingRepository b,
            CheckInRepository c,
            ComplaintRepository co,
            PromotionRepository p,
            UserRepository u,
            AuditLogRepository a,
            SeatRepository s,
            PaymentRepository pay,
            FlightRepository f,
            TicketRepository tix,
            AuditService audit,
            NotificationRepository notification,
            PassengerRepository pax,
            com.skylanka.air.seat.service.SeatService seatSvc,
            CampaignRepository camp,
            RefundRequestRepository rr,
            TicketService ticketService) {

        bookings = b;
        checkins = c;
        complaints = co;
        promotions = p;
        users = u;
        audits = a;
        seats = s;
        payments = pay;
        flights = f;
        tickets = tix;
        auditService = audit;
        notificationRepository = notification;
        passengers = pax;
        seatService = seatSvc;
        campaigns = camp;
        refundRequests = rr;
        this.ticketService = ticketService;
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @GetMapping("/checkin")
    public String checkin(Model m) {
        var allBookings = bookings.findAllByOrderByCreatedAtDesc();
        m.addAttribute("bookings", allBookings);

        Map<Long, CheckIn> checkinMap = new HashMap<>();
        for (Booking b : allBookings) {
            for (Passenger p : b.getPassengers()) {
                checkins.findByPassengerId(p.getId()).ifPresent(c -> checkinMap.put(p.getId(), c));
            }
        }
        m.addAttribute("checkinMap", checkinMap);
        return "checkin";
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/checkin/{ref}/passenger/{passengerId}")
    public String doCheckin(
            @PathVariable String ref,
            @PathVariable Long passengerId,
            @RequestParam(defaultValue = "false") boolean noShow,
            HttpSession s) {

        var b = bookings.findByReference(ref).orElseThrow();

        Passenger passenger = b.getPassengers().stream()
                .filter(p -> p.getId().equals(passengerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found on this booking."));

        var c = checkins.findByPassengerId(passengerId)
                .orElseGet(CheckIn::new);

        c.setPassenger(passenger);
        c.setNoShow(noShow);
        c.setCheckedIn(!noShow);
        c.setCheckedAt(LocalDateTime.now());

        c.setBoardingPassNumber(
                noShow
                        ? null
                        : "BP-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 10)
                        .toUpperCase()
        );

        checkins.save(c);

        // A no-show forfeits the ticket; a later genuine check-in gets a fresh (reissued) ticket.
        if (noShow) {
            ticketService.voidForPassenger(passenger);
        } else {
            ticketService.restoreAfterNoShow(passenger);
        }

        auditService.log(
                s,
                noShow ? "MARK_NO_SHOW" : "CHECK_IN",
                b.getReference() + " / " + passenger.getName(),
                noShow
                        ? "Passenger marked as no-show."
                        : "Passenger checked in."
        );

        return "redirect:/checkin";
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @GetMapping("/operations/flight/{id}/manifest")
    public String manifest(@PathVariable Long id, Model m) {
        var flight = flights.findById(id).orElseThrow();
        var manifest = passengers.findManifestByFlightId(id, BookingStatus.CANCELLED);

        Map<Long, CheckIn> checkinMap = new HashMap<>();
        for (Passenger p : manifest) {
            checkins.findByPassengerId(p.getId()).ifPresent(c -> checkinMap.put(p.getId(), c));
        }

        Map<Long, com.skylanka.air.ticket.entity.Ticket> ticketMap = new HashMap<>();
        for (Passenger p : manifest) {
            ticketService.findByPassengerId(p.getId()).ifPresent(t -> ticketMap.put(p.getId(), t));
        }

        m.addAttribute("flight", flight);
        m.addAttribute("manifest", manifest);
        m.addAttribute("checkinMap", checkinMap);
        m.addAttribute("ticketMap", ticketMap);
        m.addAttribute("availableSeats", seats.findByFlightIdAndStatusOrderBySeatNumber(id, SeatStatus.AVAILABLE));
        return "manifest";
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @GetMapping("/checkin/passenger/{passengerId}/boarding-pass")
    public org.springframework.http.ResponseEntity<byte[]> boardingPass(@PathVariable Long passengerId) throws Exception {
        var checkIn = checkins.findByPassengerId(passengerId).orElse(null);
        if (checkIn == null || checkIn.getBoardingPassNumber() == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        Passenger p = checkIn.getPassenger();
        Booking b = p.getBooking();

        byte[] out = com.skylanka.air.shared.pdf.PdfDocumentBuilder.create()
                .pageSize(org.apache.pdfbox.pdmodel.common.PDRectangle.A6)
                .startPosition(30, 260)
                .fontSizes(14, 10)
                .lineSpacing(22)
                .title("BOARDING PASS")
                .lines(
                        "Passenger: " + p.getName(),
                        "Flight: " + b.getFlight().getFlightNumber(),
                        "Route: " + b.getFlight().getOrigin() + " -> " + b.getFlight().getDestination(),
                        "Date: " + b.getFlight().getDepartureDate() + "  Time: " + b.getFlight().getDepartureTime(),
                        "Seat: " + p.getSeat().getSeatNumber() + " (" + p.getSeat().getSeatClass() + ")",
                        "Boarding pass no.: " + checkIn.getBoardingPassNumber(),
                        "Booking ref: " + b.getReference()
                )
                .build();

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=boarding-pass-" + checkIn.getBoardingPassNumber() + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(out);
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/checkin/{ref}/passenger/{passengerId}/reassign-seat")
    public String reassignSeat(
            @PathVariable String ref,
            @PathVariable Long passengerId,
            @RequestParam Long newSeatId,
            HttpSession s) {

        var b = bookings.findByReference(ref).orElseThrow();

        Passenger passenger = b.getPassengers().stream()
                .filter(p -> p.getId().equals(passengerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found on this booking."));

        var newSeat = seats.findById(newSeatId).orElseThrow(() -> new IllegalArgumentException("Seat not found."));
        var oldSeat = passenger.getSeat();

        if (!newSeat.getFlight().getId().equals(oldSeat.getFlight().getId())) {
            throw new IllegalArgumentException("The new seat must be on the same flight.");
        }
        if (newSeat.getStatus() != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("That seat is no longer available.");
        }

        oldSeat.setStatus(SeatStatus.AVAILABLE);
        seats.save(oldSeat);

        newSeat.setStatus(SeatStatus.BOOKED);
        seats.save(newSeat);

        passenger.setSeat(newSeat);
        passengers.save(passenger);

        if (!b.getPassengers().isEmpty() && b.getPassengers().get(0).getId().equals(passengerId)) {
            b.setSeat(newSeat);
            b.setSeatClass(newSeat.getSeatClass());
            bookings.save(b);
        }

        auditService.log(
                s,
                "REASSIGN_SEAT",
                b.getReference() + " / " + passenger.getName(),
                "Moved from seat " + oldSeat.getSeatNumber() + " to " + newSeat.getSeatNumber() + "."
        );

        return "redirect:/operations/flight/" + oldSeat.getFlight().getId() + "/manifest";
    }

    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    @GetMapping("/support")
    public String support(@RequestParam(required = false) String q, Model m) {

        var allBookings = bookings.findAllByOrderByCreatedAtDesc();
        List<Booking> matchedBookings = allBookings;

        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase();
            matchedBookings = allBookings.stream()
                    .filter(b -> containsIgnoreCase(b.getReference(), needle)
                            || containsIgnoreCase(b.getPassengerName(), needle)
                            || containsIgnoreCase(b.getPassengerContact(), needle)
                            || (b.getCustomer() != null && containsIgnoreCase(b.getCustomer().getEmail(), needle)))
                    .toList();
        }

        m.addAttribute("bookings", matchedBookings);
        m.addAttribute("query", q);

        var allComplaints = complaints.findAllByOrderByCreatedAtDesc();
        m.addAttribute("complaints", allComplaints);

        long openComplaintCount = allComplaints.stream()
                .filter(c -> c.getStatus() == ComplaintStatus.OPEN || c.getStatus() == ComplaintStatus.IN_PROGRESS)
                .count();
        m.addAttribute("openComplaintCount", openComplaintCount);
        m.addAttribute("agingCutoff", java.time.LocalDateTime.now().minusHours(24));

        long agingComplaintCount = allComplaints.stream()
                .filter(c -> c.getStatus() == ComplaintStatus.OPEN || c.getStatus() == ComplaintStatus.IN_PROGRESS)
                .filter(c -> c.getCreatedAt() != null
                        && c.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24)))
                .count();
        m.addAttribute("agingComplaintCount", agingComplaintCount);

        var pendingRefundRequests = refundRequests.findByStatusOrderByCreatedAtAsc(RefundRequestStatus.PENDING);
        m.addAttribute("pendingRefundRequests", pendingRefundRequests);

        return "support";
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private static final java.util.regex.Pattern SUPPORT_NAME_PATTERN =
            java.util.regex.Pattern.compile("[\\p{L} .'-]{2,100}");
    private static final java.util.regex.Pattern SUPPORT_CONTACT_PATTERN =
            java.util.regex.Pattern.compile("\\+?[0-9]{7,15}");

    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    @PostMapping("/support/booking/{ref}")
    public String update(
            @PathVariable String ref,
            @RequestParam String passengerName,
            @RequestParam String passengerContact,
            HttpSession s) {

        var b = bookings.findByReference(ref).orElseThrow();

        String name = passengerName == null ? "" : passengerName.trim();
        String contact = passengerContact == null ? "" : passengerContact.trim();

        if (!SUPPORT_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Enter a valid passenger name (2-100 letters, spaces, apostrophes or hyphens).");
        }
        if (!SUPPORT_CONTACT_PATTERN.matcher(contact).matches()) {
            throw new IllegalArgumentException(
                    "Enter a valid contact number (7-15 digits, optional leading +).");
        }

        b.setPassengerName(name);
        b.setPassengerContact(contact);
        b.setModifiedAt(LocalDateTime.now());

        if (b.getPassengers() != null && !b.getPassengers().isEmpty()) {
            var primary = b.getPassengers().get(0);
            primary.setName(name);
            primary.setContact(contact);
        }

        bookings.save(b);

        if (b.getStatus() == BookingStatus.CONFIRMED && b.getPassengers() != null && !b.getPassengers().isEmpty()) {
            ticketService.reissue(b.getPassengers().get(0));
        }

        auditService.log(
                s,
                "UPDATE_BOOKING",
                b.getReference(),
                "Passenger details updated by support."
        );

        return "redirect:/support";
    }

    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    @PostMapping("/support/complaint/{id}")
    public String complaint(
            @PathVariable Long id,
            @RequestParam ComplaintStatus status,
            @RequestParam(required = false) String response,
            HttpSession s) {

        var c = complaints.findById(id).orElseThrow();

        c.setStatus(status);
        c.setResponse(response);
        c.setUpdatedAt(LocalDateTime.now());

        complaints.save(c);

        auditService.log(
                s,
                "UPDATE_COMPLAINT",
                String.valueOf(c.getId()),
                c.getDisplayReference() + " status changed to " + status.getLabel()
        );

        if (c.getCustomer() != null) {
            Notification n = new Notification();
            n.setUser(c.getCustomer());
            n.setTitle("Complaint Updated");
            n.setMessage(
                    "Your complaint " + c.getDisplayReference() + " (\"" + c.getSubject() +
                            "\") is now: " + status.getLabel() + "."
            );

            notificationRepository.save(n);
        }

        return "redirect:/support";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MARKETING')")
    @GetMapping("/marketing")
    public String marketing(Model m) {
        m.addAttribute("promotions", promotions.findAll());
        m.addAttribute("campaigns", campaigns.findAll());

        m.addAttribute("ticketsSold", tickets.countByStatus(com.skylanka.air.shared.entity.TicketStatus.ISSUED));
        m.addAttribute(
                "salesRevenue",
                payments.sumByStatuses(java.util.List.of(PaymentStatus.PAID, PaymentStatus.VERIFIED))
        );
        m.addAttribute(
                "destinationPopularity",
                bookings.destinationPopularity(BookingStatus.CANCELLED)
        );

        Map<Long, BookingRepository.CampaignPerformance> performanceByCampaign = new HashMap<>();
        for (var row : bookings.campaignPerformance()) {
            performanceByCampaign.put(row.getCampaignId(), row);
        }
        m.addAttribute("campaignPerformance", performanceByCampaign);

        m.addAttribute(
                "bookingTrend",
                bookings.bookingCountByDay(LocalDateTime.now().minusDays(30))
        );

        m.addAttribute("retentionRate", customerRetentionRate());
        m.addAttribute("optedInCount", users.findByRoleAndMarketingOptInTrueAndGuestFalse(Role.CUSTOMER).size());

        return "marketing";
    }

    private BigDecimal customerRetentionRate() {
        long totalCustomers = users.countByRole(Role.CUSTOMER);
        if (totalCustomers == 0) return BigDecimal.ZERO;

        long repeatCustomers = bookings.repeatCustomerIds(BookingStatus.CANCELLED).size();

        return BigDecimal.valueOf(repeatCustomers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCustomers), 1, java.math.RoundingMode.HALF_UP);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MARKETING')")
    @PostMapping("/marketing/campaign")
    public String createCampaign(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            HttpSession s) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Campaign name is required.");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Campaign end date cannot be before its start date.");
        }

        Campaign c = new Campaign();
        c.setName(name.trim());
        c.setDescription(description);
        c.setStartDate(startDate);
        c.setEndDate(endDate);
        campaigns.save(c);

        auditService.log(s, "CREATE_CAMPAIGN", c.getName(), "Campaign created.");

        return "redirect:/marketing";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MARKETING')")
    @PostMapping("/marketing/communication")
    public String sendCommunication(
            @RequestParam String subject,
            @RequestParam String message,
            @RequestParam(defaultValue = "ALL") String segment,
            HttpSession s) {

        if (subject == null || subject.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Subject and message are required.");
        }

        // Marketing goes only to customers who explicitly opted in (transactional notices are separate).
        List<User> targets = "RECENT_BOOKERS".equals(segment)
                ? bookings.findDistinctCustomersBookedSince(LocalDateTime.now().minusDays(30)).stream()
                        .filter(u -> u.isMarketingOptIn() && !u.isGuest())
                        .toList()
                : users.findByRoleAndMarketingOptInTrueAndGuestFalse(Role.CUSTOMER);

        for (User u : targets) {
            Notification n = new Notification();
            n.setUser(u);
            n.setTitle(subject.trim());
            n.setMessage(message.trim());
            notificationRepository.save(n);
        }

        auditService.log(
                s,
                "SEND_MARKETING_MESSAGE",
                segment,
                targets.size() + " opted-in customer(s) notified: " + subject.trim()
        );

        return "redirect:/marketing";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MARKETING')")
    @PostMapping("/marketing/promotion")
    public String promotion(
            @RequestParam String code,
            @RequestParam double percentage,
            @RequestParam LocalDate validFrom,
            @RequestParam LocalDate validUntil,
            @RequestParam(required = false) Long campaignId,
            HttpSession s) {

        if (percentage <= 0 || percentage > 100)
            throw new IllegalArgumentException(
                    "Discount percentage must be between 1 and 100."
            );

        if (validUntil.isBefore(validFrom))
            throw new IllegalArgumentException(
                    "Promotion end date cannot be before start date."
            );

        String normalizedCode = code.trim().toUpperCase();

        if (promotions.findByCode(normalizedCode).isPresent())
            throw new IllegalArgumentException(
                    "Promotion code already exists."
            );

        Promotion p = new Promotion();

        p.setCode(normalizedCode);
        p.setPercentage(percentage);
        p.setValidFrom(validFrom);
        p.setValidUntil(validUntil);
        p.setActive(true);
        if (campaignId != null) {
            p.setCampaign(campaigns.findById(campaignId).orElse(null));
        }

        promotions.save(p);

        auditService.log(
                s,
                "CREATE_PROMOTION",
                normalizedCode,
                percentage + "% promotion created."
        );

        return "redirect:/marketing";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MARKETING')")
    @PostMapping("/marketing/promotion/{id}/toggle")
    public String togglePromo(
            @PathVariable Long id,
            HttpSession s) {

        var p = promotions.findById(id).orElseThrow();

        p.setActive(!p.isActive());
        promotions.save(p);

        auditService.log(
                s,
                "TOGGLE_PROMOTION",
                p.getCode(),
                "Promotion active = " + p.isActive()
        );

        return "redirect:/marketing";
    }

    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @GetMapping("/reports")
    public String reports(Model m) {

        BigDecimal revenue = payments.sumByStatuses(
                java.util.List.of(
                        PaymentStatus.PAID,
                        PaymentStatus.VERIFIED
                )
        );

        BigDecimal refunds = payments.sumByStatus(
                PaymentStatus.REFUNDED
        );

        m.addAttribute("revenue", revenue);
        m.addAttribute("refunds", refunds);

        long confirmed = bookings.countByStatus(BookingStatus.CONFIRMED);
        long pending = bookings.countByStatus(BookingStatus.PENDING);
        long cancelled = bookings.countByStatus(BookingStatus.CANCELLED);
        long totalBookings = bookings.count();

        m.addAttribute("confirmed", confirmed);
        m.addAttribute("pending", pending);
        m.addAttribute("cancelled", cancelled);

        m.addAttribute(
                "cancellationRate",
                totalBookings == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(cancelled)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(totalBookings), 1, java.math.RoundingMode.HALF_UP)
        );

        Map<Long, Long> bookedByFlight = new HashMap<>();
        for (var row : seats.countBookedByFlight(SeatStatus.BOOKED)) {
            bookedByFlight.put(row.getFlightId(), row.getBookedCount());
        }

        List<Map<String, Object>> loadFactors = new java.util.ArrayList<>();
        for (var f : flights.findAll()) {
            if (f.getStatus() == FlightStatus.CANCELLED) continue;

            long booked = bookedByFlight.getOrDefault(f.getId(), 0L);
            BigDecimal factor = f.getSeatCapacity() == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(booked)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(f.getSeatCapacity()), 1, java.math.RoundingMode.HALF_UP);

            Map<String, Object> row = new HashMap<>();
            row.put("flightNumber", f.getFlightNumber());
            row.put("route", f.getOrigin() + " → " + f.getDestination());
            row.put("booked", booked);
            row.put("capacity", f.getSeatCapacity());
            row.put("loadFactor", factor);
            loadFactors.add(row);
        }
        loadFactors.sort((a, b2) ->
                ((BigDecimal) b2.get("loadFactor")).compareTo((BigDecimal) a.get("loadFactor")));
        m.addAttribute("loadFactors", loadFactors);

        m.addAttribute(
                "routeRevenue",
                payments.revenueByRoute(java.util.List.of(PaymentStatus.PAID, PaymentStatus.VERIFIED))
        );

        return "reports";
    }
}
