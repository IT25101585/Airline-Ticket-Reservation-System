package com.skylanka.air.ticket.controller;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.shared.entity.TicketStatus;
import com.skylanka.air.shared.security.AuthorizationSupport;
import com.skylanka.air.ticket.entity.Ticket;
import com.skylanka.air.ticket.repository.TicketRepository;
import com.skylanka.air.ticket.service.TicketService;
import com.skylanka.air.shared.service.AuditService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@Controller
public class TicketController {
    private final BookingRepository bookings;
    private final TicketRepository tickets;
    private final TicketService service;
    private final AuthorizationSupport authz;
    private final AuditService auditService;

    public TicketController(BookingRepository b, TicketRepository t, TicketService s, AuthorizationSupport authz,
                            AuditService auditService) {
        bookings = b;
        tickets = t;
        service = s;
        this.authz = authz;
        this.auditService = auditService;
    }

    /** Dedicated staff-facing Ticket Management dashboard used by Operations/Admin. */
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @GetMapping("/operations/tickets")
    public String dashboard(Model model) {
        List<Ticket> allTickets = tickets.findAll().stream()
                .sorted(Comparator.comparing(Ticket::getIssuedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        model.addAttribute("tickets", allTickets);
        model.addAttribute("totalTickets", allTickets.size());
        model.addAttribute("issuedTickets", tickets.countByStatus(TicketStatus.ISSUED));
        model.addAttribute("reissuedTickets", tickets.countByStatus(TicketStatus.REISSUED));
        model.addAttribute("voidTickets", tickets.countByStatus(TicketStatus.VOID));
        return "ticket-management";
    }

    /** Operations/Admin can download a live e-ticket directly from Ticket Management. */
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @GetMapping("/operations/ticket/{ticketNumber}/pdf")
    public ResponseEntity<byte[]> staffPdf(@PathVariable String ticketNumber) throws Exception {
        Ticket t = tickets.findByTicketNumber(ticketNumber).orElseThrow();
        return renderPdf(t);
    }

    @GetMapping("/ticket/{ref}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String ref) throws Exception {
        Booking b = bookings.findByReference(ref).orElseThrow();
        if (!canView(b)) return ResponseEntity.status(403).build();

        List<Ticket> forBooking = tickets.findByBookingId(b.getId());
        // Prefer live tickets; superseded (REISSUED) ones only matter as history.
        List<Ticket> live = forBooking.stream().filter(x -> x.getStatus() != TicketStatus.REISSUED).toList();
        if (!live.isEmpty()) forBooking = live;
        if (forBooking.isEmpty()) return ResponseEntity.notFound().build();

        Ticket t = forBooking.stream()
                .filter(ticket -> ticket.getPassenger().getId()
                        .equals(b.getPassengers().isEmpty() ? null : b.getPassengers().get(0).getId()))
                .findFirst()
                .orElse(forBooking.get(0));

        return renderPdf(t);
    }

    @GetMapping("/ticket/{ref}/pdf/{ticketNumber}")
    public ResponseEntity<byte[]> pdfForPassenger(
            @PathVariable String ref,
            @PathVariable String ticketNumber) throws Exception {

        Booking b = bookings.findByReference(ref).orElseThrow();
        if (!canView(b)) return ResponseEntity.status(403).build();

        Ticket t = tickets.findByTicketNumber(ticketNumber).orElseThrow();
        if (!t.getBooking().getId().equals(b.getId())) {
            return ResponseEntity.status(404).build();
        }

        return renderPdf(t);
    }

    private ResponseEntity<byte[]> renderPdf(Ticket t) throws Exception {
        if (t.getStatus() == TicketStatus.VOID || t.getStatus() == TicketStatus.REISSUED) {
            return ResponseEntity.status(410).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + t.getTicketNumber() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.pdf(t));
    }

    /** Operations: reissue a passenger's ticket (new number/QR; the old one is marked REISSUED). */
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/ticket/{ticketNumber}/reissue")
    public String reissue(@PathVariable String ticketNumber, @RequestParam String ref,
                          @RequestParam(required = false) String returnTo, HttpSession s) {
        var result = service.reissueByNumber(ticketNumber);
        if (result.isEmpty()) return "redirect:" + returnUrl(ref, returnTo);
        auditService.log(s, "REISSUE_TICKET", ticketNumber, "Replaced by " + result.get().getTicketNumber());
        return "redirect:" + returnUrl(ref, returnTo);
    }

    /** Operations: void a passenger's live ticket. */
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/ticket/{ticketNumber}/void")
    public String voidTicket(@PathVariable String ticketNumber, @RequestParam String ref,
                             @RequestParam(required = false) String returnTo, HttpSession s) {
        if (service.voidByNumber(ticketNumber)) {
            auditService.log(s, "VOID_TICKET", ticketNumber, "Voided by operations");
        }
        return "redirect:" + returnUrl(ref, returnTo);
    }

    private String returnUrl(String ref, String returnTo) {
        if ("tickets".equalsIgnoreCase(returnTo)) return "/operations/tickets";
        return manifestUrl(ref);
    }

    private String manifestUrl(String ref) {
        return bookings.findByReference(ref)
                .map(b -> "/operations/flight/" + b.getFlight().getId() + "/manifest")
                .orElse("/operations");
    }

    private boolean canView(Booking b) {
        return authz.isOwnerOrHasAnyRole(
                b.getCustomer().getId(),
                "SUPPORT", "FINANCE", "ADMIN");
    }
}
