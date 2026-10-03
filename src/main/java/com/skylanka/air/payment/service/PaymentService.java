package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.payment.entity.Payment;
import com.skylanka.air.payment.entity.PaymentMethod;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.seat.repository.SeatRepository;
import com.skylanka.air.shared.entity.*;
import com.skylanka.air.ticket.service.TicketService;
import com.skylanka.air.shared.event.DomainEvent;
import com.skylanka.air.shared.event.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final TicketService tickets;
    private final PaymentGatewayFactory gateways;
    private final DomainEventPublisher events;

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentService(
            PaymentRepository p,
            BookingRepository b,
            SeatRepository s,
            TicketService t,
            PaymentGatewayFactory gateways,
            DomainEventPublisher events) {

        payments = p;
        bookings = b;
        seats = s;
        tickets = t;
        this.gateways = gateways;
        this.events = events;
    }

    private com.skylanka.air.user.service.LoyaltyService loyalty;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLoyalty(com.skylanka.air.user.service.LoyaltyService loyalty) {
        this.loyalty = loyalty;
    }

    private static final java.util.regex.Pattern CARD_NUMBER_PATTERN =
            java.util.regex.Pattern.compile("[0-9]{12,19}");
    private static final java.util.regex.Pattern CARD_EXPIRY_PATTERN =
            java.util.regex.Pattern.compile("(0[1-9]|1[0-2])/([0-9]{2})");
    private static final java.util.regex.Pattern CARD_CVV_PATTERN =
            java.util.regex.Pattern.compile("[0-9]{3,4}");
    private static final java.util.regex.Pattern BANK_ACCOUNT_PATTERN =
            java.util.regex.Pattern.compile("[0-9]{8,20}");

    /** Kept for callers (and existing tests) that don't need to supply mock payment details. */
    @Transactional
    public Payment pay(Booking b, PaymentMethod method) {
        return pay(b, method, null, null, null, null);
    }

    @Transactional(noRollbackFor = PaymentDeclinedException.class)
    public Payment pay(
            Booking b, PaymentMethod method,
            String cardNumber, String cardExpiry, String cardCvv, String bankAccountNumber) {

        if (b.getStatus() != BookingStatus.PENDING)
            throw new IllegalStateException(
                    "Only pending bookings can be paid."
            );

        if (method == null) {
            throw new IllegalArgumentException("Please choose a valid payment method.");
        }

        if (b.getFlight() == null
                || b.getFlight().getStatus() == FlightStatus.CANCELLED
                || hasDeparted(b.getFlight())) {

            throw new IllegalStateException(
                    "This booking's flight has been cancelled or has already departed and can no longer be paid for."
            );
        }

        if (b.getTotalFare() == null || b.getTotalFare().signum() <= 0) {
            throw new IllegalStateException(
                    "This booking does not have a valid amount due. Please contact support."
            );
        }

        validatePaymentDetails(method, cardNumber, cardExpiry, cardCvv, bankAccountNumber);

        if (payments.findByBookingId(b.getId())
                .filter(p ->
                        p.getStatus() == PaymentStatus.PAID ||
                                p.getStatus() == PaymentStatus.VERIFIED
                )
                .isPresent()) {

            throw new IllegalStateException(
                    "Payment has already been completed."
            );
        }

        // Every attempt is its own payment row: a declined attempt stays on record as FAILED
        // and a retry creates a new one, so a booking can hold one or more payments.
        Payment p = payments.findByBookingId(b.getId())
                .filter(existing -> existing.getStatus() == PaymentStatus.PENDING)
                .orElseGet(Payment::new);

        p.setBooking(b);
        p.setAmount(b.getTotalFare());
        p.setMethod(method);

        PaymentGateway.AuthorizationResult authorization =
                gateways.forMethod(method).authorize(b, method, cardNumber);
        if (!authorization.approved()) {
            if (authorization.transactionId() == null && cardNumber != null
                    && cardNumber.replace(" ", "").equals(MockPaymentGateway.DECLINE_TEST_CARD)) {
                p.setTransactionId("FAILED-TX-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
                p.setStatus(PaymentStatus.FAILED);
                p.setUpdatedAt(LocalDateTime.now());
                payments.save(p);
                announce(DomainEvent.Type.PAYMENT_DECLINED, b, "Payment Declined",
                        "Your payment for booking " + b.getReference() + " was declined. You can retry from the booking page.");
                throw new PaymentDeclinedException(authorization.message());
            }
            throw new IllegalStateException(authorization.message());
        }
        p.setTransactionId(authorization.transactionId());

        p.setStatus(PaymentStatus.PAID);
        p.setUpdatedAt(LocalDateTime.now());

        b.setStatus(BookingStatus.CONFIRMED);
        b.setModifiedAt(LocalDateTime.now());

        bookings.save(b);

        Payment saved = payments.save(p);
        tickets.issue(b);
        if (loyalty != null) {
            loyalty.award(b.getCustomer(), p.getAmount());
        }

        announce(
                DomainEvent.Type.PAYMENT_COMPLETED,
                b,
                "Payment Successful",
                "Payment for booking " +
                        b.getReference() +
                        " was successful."
        );

        return saved;
    }

    /**
     * Which current payment statuses may be manually moved to a given target status
     * via the finance override screen ({@code /finance/payment/{id}}) or an approved
     * refund request. This keeps staff from pushing a payment into a state that
     * doesn't make business sense, e.g. "refunding" money that was never collected,
     * or re-verifying a payment that has already been voided or refunded.
     */
    private static final java.util.Map<PaymentStatus, java.util.Set<PaymentStatus>> ALLOWED_STATUS_TRANSITIONS =
            java.util.Map.of(
                    PaymentStatus.VERIFIED, java.util.EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PAID),
                    PaymentStatus.VOID, java.util.EnumSet.of(PaymentStatus.PENDING, PaymentStatus.FAILED),
                    PaymentStatus.REFUNDED, java.util.EnumSet.of(PaymentStatus.PAID, PaymentStatus.VERIFIED),
                    PaymentStatus.FAILED, java.util.EnumSet.of(PaymentStatus.PENDING)
            );

    @Transactional
    public void updateStatus(Payment p, PaymentStatus status) {

        if (status == null) {
            throw new IllegalArgumentException("A payment status is required.");
        }

        PaymentStatus current = p.getStatus();

        if (status != current) {
            java.util.Set<PaymentStatus> allowedFrom = ALLOWED_STATUS_TRANSITIONS.get(status);
            if (allowedFrom != null && !allowedFrom.contains(current)) {
                throw new IllegalStateException(
                        "Payment cannot be changed from " + current + " to " + status + "."
                );
            }
        }

        p.setStatus(status);
        p.setUpdatedAt(LocalDateTime.now());

        payments.save(p);

        Booking b = p.getBooking();

        if (b == null)
            return;

        boolean wasPaid = current == PaymentStatus.PAID || current == PaymentStatus.VERIFIED;
        if (loyalty != null) {
            if (status == PaymentStatus.VERIFIED && !wasPaid) {
                loyalty.award(b.getCustomer(), p.getAmount());
            } else if ((status == PaymentStatus.REFUNDED || status == PaymentStatus.VOID) && wasPaid) {
                loyalty.revoke(b.getCustomer(), p.getAmount());
            }
        }

        if (status == PaymentStatus.VERIFIED) {
            if (b.getStatus() == BookingStatus.CANCELLED) {
                throw new IllegalStateException("A cancelled booking cannot be verified.");
            }

            b.setStatus(BookingStatus.CONFIRMED);
            b.setModifiedAt(LocalDateTime.now());

            bookings.save(b);

            tickets.issue(b);

            announce(
                    DomainEvent.Type.PAYMENT_VERIFIED,
                    b,
                    "Payment Verified",
                    "Your payment for booking " +
                            b.getReference() +
                            " has been verified. Your ticket has been issued."
            );
        }

        if (status == PaymentStatus.REFUNDED ||
                status == PaymentStatus.VOID) {

            if (b.getStatus() != BookingStatus.CANCELLED) {

                b.setStatus(BookingStatus.CANCELLED);
                b.setModifiedAt(LocalDateTime.now());

                bookings.save(b);
            }

            if (b.getSeat() != null &&
                    b.getSeat().getStatus() != SeatStatus.BLOCKED) {

                b.getSeat().setStatus(SeatStatus.AVAILABLE);
                seats.save(b.getSeat());
            }

            tickets.voidTicket(b);

            announce(
                    status == PaymentStatus.REFUNDED
                            ? DomainEvent.Type.PAYMENT_REFUNDED
                            : DomainEvent.Type.PAYMENT_VOIDED,
                    b,
                    status == PaymentStatus.REFUNDED
                            ? "Payment Refunded"
                            : "Payment Voided",
                    "Your payment for booking " +
                            b.getReference() +
                            " was " + status.name().toLowerCase() +
                            ". The booking has been cancelled."
            );
        }
    }

    /** Observer pattern: tell every registered observer (in-app, email, log...) about a payment event. */
    private void announce(
            DomainEvent.Type type,
            Booking booking,
            String title,
            String message) {

        events.publish(new DomainEvent(type, booking, booking.getCustomer(), title, message));
    }

    /** Finance-generated invoice for a payment (same fare breakdown as the receipt, with an invoice number). */
    public byte[] invoicePdf(Payment p) throws java.io.IOException {
        Booking b = p.getBooking();
        String customerName = b.getCustomer() != null ? b.getCustomer().getName() : "-";
        String customerEmail = b.getCustomer() != null ? b.getCustomer().getEmail() : "-";

        return com.skylanka.air.shared.pdf.PdfDocumentBuilder.create()
                .pageSize(org.apache.pdfbox.pdmodel.common.PDRectangle.A4)
                .startPosition(60, 760)
                .title("SKYLANKA AIR - TAX INVOICE")
                .lines(
                        "Invoice no: INV-" + String.format("%06d", p.getId()),
                        "Invoice date: " + LocalDateTime.now().toLocalDate(),
                        "Transaction ID: " + p.getTransactionId(),
                        "Booking reference: " + b.getReference(),
                        "Billed to: " + customerName + " <" + customerEmail + ">",
                        "Flight: " + b.getFlight().getFlightNumber()
                                + " (" + b.getFlight().getOrigin() + " -> " + b.getFlight().getDestination() + ")",
                        "Payment status: " + p.getStatus()
                )
                .blankLine()
                .lines(
                        "Base fare: LKR " + b.getBaseFare(),
                        "Tax: LKR " + b.getTax(),
                        "Service fee: LKR " + b.getServiceFee(),
                        "Discount: LKR " + (b.getDiscount() == null ? "0.00" : b.getDiscount()),
                        "Amount due: LKR " + p.getAmount()
                )
                .build();
    }

    /** Payment receipt PDF; layout lives in {@link com.skylanka.air.shared.pdf.ReceiptPdf}. */
    public byte[] receiptPdf(Payment p) throws java.io.IOException {
        Booking b = p.getBooking();
        var f = b.getFlight();
        var dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH);
        var stampFmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", java.util.Locale.ENGLISH);

        var dep = f.getDepartureAirport();
        var arr = f.getArrivalAirport();
        String from = dep != null && dep.getAirportCode() != null
                ? dep.getCity() + " (" + dep.getAirportCode() + ")" : f.getOrigin();
        String to = arr != null && arr.getAirportCode() != null
                ? arr.getCity() + " (" + arr.getAirportCode() + ")" : f.getDestination();

        PaymentMethod m = p.getMethod();
        String kind = m == PaymentMethod.BANK_TRANSFER ? "BANK_TRANSFER"
                : (m == PaymentMethod.VISA || m == PaymentMethod.MASTERCARD) ? "CARD" : "OTHER";

        var names = new java.util.ArrayList<String>();
        for (var pax : b.getPassengers()) names.add(pax.getName());

        var data = new com.skylanka.air.shared.pdf.ReceiptPdfData(
                "RCT-" + String.format("%06d", p.getId()), LocalDateTime.now().toLocalDate().format(dateFmt),
                b.getCustomer() != null ? b.getCustomer().getName() : "-",
                b.getCustomer() != null ? b.getCustomer().getEmail() : null,
                b.getCustomer() != null ? b.getCustomer().getContactNumber() : null,
                b.getReference(), String.valueOf(b.getId()),
                b.getCreatedAt() == null ? null : b.getCreatedAt().format(stampFmt),
                b.getStatus() == null ? null : b.getStatus().name(),
                f.getFlightNumber(), f.getAirline(), from + " to " + to,
                f.getDepartureDate() == null ? null : f.getDepartureDate().format(dateFmt),
                f.getDepartureTime() == null ? null : f.getDepartureTime().toString(),
                b.getSeatClass(), names,
                kind, m != null ? m.getLabel() : "-", p.getTransactionId(),
                p.getCreatedAt() == null ? null : p.getCreatedAt().format(stampFmt),
                p.getStatus() == null ? null : p.getStatus().name(),
                "LKR", receiptMoney(b.getBaseFare()), receiptMoney(b.getTax()), receiptMoney(b.getServiceFee()),
                receiptMoney(b.getDiscount() == null ? java.math.BigDecimal.ZERO : b.getDiscount()),
                b.getPromoCode(), receiptMoney(p.getAmount()),
                p.getRefundAmount() == null ? null : receiptMoney(p.getRefundAmount()));

        return com.skylanka.air.shared.pdf.ReceiptPdf.render(data);
    }

    private static String receiptMoney(java.math.BigDecimal v) {
        return v == null ? null : String.format(java.util.Locale.US, "%,.2f", v);
    }

    private void validatePaymentDetails(
            PaymentMethod method, String cardNumber, String cardExpiry, String cardCvv, String bankAccountNumber) {

        if (method == PaymentMethod.VISA || method == PaymentMethod.MASTERCARD) {
            String number = cardNumber == null ? "" : cardNumber.replace(" ", "");
            if (!CARD_NUMBER_PATTERN.matcher(number).matches()) {
                throw new IllegalArgumentException("Enter a valid card number (12-19 digits).");
            }
            if (cardExpiry == null || !CARD_EXPIRY_PATTERN.matcher(cardExpiry.trim()).matches()) {
                throw new IllegalArgumentException("Enter a valid card expiry (MM/YY).");
            }
            if (cardCvv == null || !CARD_CVV_PATTERN.matcher(cardCvv.trim()).matches()) {
                throw new IllegalArgumentException("Enter a valid CVV (3-4 digits).");
            }

            java.util.regex.Matcher expiryMatch = CARD_EXPIRY_PATTERN.matcher(cardExpiry.trim());
            expiryMatch.matches();
            int expMonth = Integer.parseInt(expiryMatch.group(1));
            int expYear = 2000 + Integer.parseInt(expiryMatch.group(2));
            java.time.YearMonth expiry = java.time.YearMonth.of(expYear, expMonth);
            if (expiry.isBefore(java.time.YearMonth.now())) {
                throw new IllegalArgumentException("This card has expired.");
            }
        } else if (method == PaymentMethod.BANK_TRANSFER) {
            String account = bankAccountNumber == null ? "" : bankAccountNumber.replace(" ", "");
            if (!BANK_ACCOUNT_PATTERN.matcher(account).matches()) {
                throw new IllegalArgumentException("Enter a valid bank account number (8-20 digits).");
            }
        }
    }

    private boolean hasDeparted(com.skylanka.air.flight.entity.Flight flight) {
        return LocalDateTime.of(
                flight.getDepartureDate(),
                flight.getDepartureTime()
        ).isBefore(LocalDateTime.now());
    }
}