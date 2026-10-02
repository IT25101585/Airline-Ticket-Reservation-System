package com.skylanka.air.payment.controller;

import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.payment.entity.PaymentMethod;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.payment.service.PaymentService;
import com.skylanka.air.shared.entity.PaymentStatus;
import com.skylanka.air.shared.security.AuthorizationSupport;
import com.skylanka.air.ticket.service.TicketService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PaymentController {

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentService service;
    private final TicketService tickets;
    private final AuthorizationSupport authz;

    public PaymentController(
            BookingRepository b,
            PaymentRepository p,
            PaymentService s,
            TicketService t,
            AuthorizationSupport authz) {

        bookings = b;
        payments = p;
        service = s;
        tickets = t;
        this.authz = authz;
    }

    @PostMapping("/booking/{ref}/pay")
    public String pay(
            @PathVariable String ref,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String cardNumber,
            @RequestParam(required = false) String cardExpiry,
            @RequestParam(required = false) String cardCvv,
            @RequestParam(required = false) String bankAccountNumber,
            Model m) {

        var b = bookings.findByReference(ref).orElse(null);
        if (b == null) return "redirect:/dashboard";

        if (!authz.hasAnyRole("CUSTOMER") || !authz.isOwner(b.getCustomer().getId())) {
            return "redirect:/login";
        }

        PaymentMethod paymentMethod;
        try {
            paymentMethod = method == null ? null : PaymentMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            paymentMethod = null;
        }

        try {

            service.pay(b, paymentMethod, cardNumber, cardExpiry, cardCvv, bankAccountNumber);

        } catch (Exception e) {

            m.addAttribute("error", e.getMessage());
            m.addAttribute("booking", b);
            m.addAttribute(
                    "payment",
                    payments.findByBookingId(b.getId()).orElse(null)
            );
            m.addAttribute(
                    "tickets",
                    tickets.findByBookingId(b.getId())
            );
            m.addAttribute("refundRequests", java.util.List.of());
            m.addAttribute("canManage", true);

            // Re-populate what the customer entered so a failed attempt doesn't force them
            // to retype everything. CVV is deliberately left out - it should never be echoed
            // back to the page, even on a validation error.
            m.addAttribute("submittedMethod", method);
            m.addAttribute("submittedCardNumber", cardNumber);
            m.addAttribute("submittedCardExpiry", cardExpiry);
            m.addAttribute("submittedBankAccountNumber", bankAccountNumber);

            return "booking";
        }

        return "redirect:/booking/" + ref;
    }

    @PreAuthorize("hasRole('FINANCE')")
    @GetMapping("/finance")
    public String finance(Model m) {
        var allPayments = payments.findAllByOrderByCreatedAtDesc();
        m.addAttribute("payments", allPayments);

        var now = java.time.LocalDateTime.now();
        var startOfToday = now.toLocalDate().atStartOfDay();
        var startOfWeek = now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1).atStartOfDay();
        var startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay();

        java.util.function.Predicate<com.skylanka.air.payment.entity.Payment> isRevenue =
                p -> p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.VERIFIED;

        m.addAttribute("revenueToday", sumSince(allPayments, isRevenue, startOfToday));
        m.addAttribute("revenueWeek", sumSince(allPayments, isRevenue, startOfWeek));
        m.addAttribute("revenueMonth", sumSince(allPayments, isRevenue, startOfMonth));

        long pendingCount = allPayments.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).count();
        long failedCount = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED || p.getStatus() == PaymentStatus.VOID)
                .count();
        m.addAttribute("pendingCount", pendingCount);
        m.addAttribute("failedCount", failedCount);

        java.util.Map<String, Long> methodCounts = new java.util.LinkedHashMap<>();
        for (PaymentMethod pm : PaymentMethod.values()) {
            methodCounts.put(pm.name(), 0L);
        }
        for (var p : allPayments) {
            if (p.getMethod() != null) methodCounts.merge(p.getMethod().name(), 1L, Long::sum);
        }
        m.addAttribute("methodCounts", methodCounts);

        var outstandingBookings = bookings.findAllByOrderByCreatedAtDesc().stream()
                .filter(b -> b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.CONFIRMED
                        || b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.PENDING)
                .filter(b -> payments.findByBookingId(b.getId())
                        .map(p -> p.getStatus() != PaymentStatus.PAID && p.getStatus() != PaymentStatus.VERIFIED)
                        .orElse(true))
                .toList();

        java.math.BigDecimal outstandingBalance = outstandingBookings.stream()
                .map(com.skylanka.air.booking.entity.Booking::getTotalFare)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        m.addAttribute("outstandingBalance", outstandingBalance);
        m.addAttribute("outstandingCount", outstandingBookings.size());

        return "finance";
    }

    private java.math.BigDecimal sumSince(
            java.util.List<com.skylanka.air.payment.entity.Payment> allPayments,
            java.util.function.Predicate<com.skylanka.air.payment.entity.Payment> statusFilter,
            java.time.LocalDateTime since) {

        return allPayments.stream()
                .filter(statusFilter)
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(since))
                .map(com.skylanka.air.payment.entity.Payment::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    @PreAuthorize("hasRole('FINANCE')")
    @PostMapping("/finance/payment/{id}")
    public String verify(
            @PathVariable Long id,
            @RequestParam(required = false) String status) {

        var p = payments.findById(id).orElse(null);
        if (p == null) {
            return "redirect:/finance?error=not-found";
        }

        PaymentStatus target;
        try {
            target = status == null ? null : PaymentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            target = null;
        }

        if (target == null) {
            return "redirect:/finance?error=" +
                    java.net.URLEncoder.encode("Please choose a valid status.", java.nio.charset.StandardCharsets.UTF_8);
        }

        try {
            service.updateStatus(p, target);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return "redirect:/finance?error=" +
                    java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }

        return "redirect:/finance";
    }

    @PreAuthorize("hasRole('FINANCE')")
    @GetMapping("/finance/payment/{id}/invoice")
    public org.springframework.http.ResponseEntity<byte[]> invoice(@PathVariable Long id) throws Exception {
        var p = payments.findById(id).orElse(null);
        if (p == null || p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.FAILED) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=invoice-" + p.getBooking().getReference() + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(service.invoicePdf(p));
    }

    @GetMapping("/booking/{ref}/receipt")
    public org.springframework.http.ResponseEntity<byte[]> receipt(@PathVariable String ref) throws Exception {
        var b = bookings.findByReference(ref).orElse(null);
        if (b == null) return org.springframework.http.ResponseEntity.notFound().build();

        if (!authz.isOwnerOrHasAnyRole(b.getCustomer().getId(), "SUPPORT", "FINANCE", "ADMIN")) {
            return org.springframework.http.ResponseEntity.status(403).build();
        }

        var payment = payments.findByBookingId(b.getId()).orElse(null);
        if (payment == null || payment.getStatus() == PaymentStatus.PENDING
                || payment.getStatus() == PaymentStatus.FAILED) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=receipt-" + b.getReference() + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(service.receiptPdf(payment));
    }
}
