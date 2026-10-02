package com.skylanka.air.payment.controller;

import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.payment.entity.RefundRequest;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.payment.repository.RefundRequestRepository;
import com.skylanka.air.payment.service.PaymentService;
import com.skylanka.air.shared.entity.PaymentStatus;
import com.skylanka.air.shared.entity.RefundRequestStatus;
import com.skylanka.air.shared.security.AuthorizationSupport;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class RefundRequestController {

    private final RefundRequestRepository requests;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final AuthorizationSupport authz;

    public RefundRequestController(
            RefundRequestRepository r,
            BookingRepository b,
            PaymentRepository p,
            PaymentService ps,
            AuthorizationSupport a) {

        requests = r;
        bookings = b;
        payments = p;
        paymentService = ps;
        authz = a;
    }

    @PostMapping("/booking/{ref}/refund-request")
    public String create(@PathVariable String ref, @RequestParam String reason, Model m) {
        var b = bookings.findByReference(ref).orElse(null);
        Long uid = authz.currentUserId();
        if (b == null || uid == null || !b.getCustomer().getId().equals(uid)) {
            return "redirect:/dashboard";
        }

        if (reason == null || reason.trim().length() < 10) {
            return "redirect:/booking/" + ref + "?error=refund-reason";
        }

        if (requests.findFirstByBookingIdAndStatus(b.getId(), RefundRequestStatus.PENDING).isPresent()) {
            return "redirect:/booking/" + ref + "?error=refund-pending";
        }

        RefundRequest rr = new RefundRequest();
        rr.setBooking(b);
        rr.setCustomer(b.getCustomer());
        rr.setReason(reason.trim());
        requests.save(rr);

        return "redirect:/booking/" + ref + "?refundRequested=true";
    }

    @PreAuthorize("hasAnyRole('FINANCE')")
    @GetMapping("/finance/refund-requests")
    public String list(Model m) {
        m.addAttribute("pending", requests.findByStatusOrderByCreatedAtAsc(RefundRequestStatus.PENDING));
        return "refund-requests";
    }

    @PreAuthorize("hasAnyRole('FINANCE')")
    @PostMapping("/finance/refund-requests/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String note) {
        RefundRequest rr = requests.findById(id).orElseThrow();
        if (rr.getStatus() != RefundRequestStatus.PENDING) return "redirect:/finance/refund-requests";

        var payment = payments.findByBookingId(rr.getBooking().getId()).orElse(null);
        boolean refundable = payment != null
                && (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.VERIFIED);

        if (!refundable) {
            rr.setStatus(RefundRequestStatus.REJECTED);
            rr.setFinanceNote("No completed payment exists on this booking to refund.");
            rr.setResolvedAt(LocalDateTime.now());
            requests.save(rr);
            return "redirect:/finance/refund-requests";
        }

        payment.setRefundAmount(payment.getAmount());

        try {
            paymentService.updateStatus(payment, PaymentStatus.REFUNDED);
        } catch (IllegalStateException e) {
            rr.setStatus(RefundRequestStatus.REJECTED);
            rr.setFinanceNote(e.getMessage());
            rr.setResolvedAt(LocalDateTime.now());
            requests.save(rr);
            return "redirect:/finance/refund-requests";
        }

        rr.setStatus(RefundRequestStatus.APPROVED);
        rr.setFinanceNote(note);
        rr.setResolvedAt(LocalDateTime.now());
        requests.save(rr);

        return "redirect:/finance/refund-requests";
    }

    @PreAuthorize("hasAnyRole('FINANCE')")
    @PostMapping("/finance/refund-requests/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String note) {
        RefundRequest rr = requests.findById(id).orElseThrow();
        if (rr.getStatus() != RefundRequestStatus.PENDING) return "redirect:/finance/refund-requests";

        rr.setStatus(RefundRequestStatus.REJECTED);
        rr.setFinanceNote(note);
        rr.setResolvedAt(LocalDateTime.now());
        requests.save(rr);

        return "redirect:/finance/refund-requests";
    }
}
