package com.skylanka.air.booking.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.entity.Passenger;
import com.skylanka.air.booking.repository.BookingRepository;
import java.util.regex.Pattern;
import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.flight.repository.FlightRepository;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.payment.service.RefundPolicyService;
import com.skylanka.air.seat.entity.Seat;
import com.skylanka.air.seat.repository.SeatRepository;
import com.skylanka.air.shared.entity.*;
import com.skylanka.air.shared.repository.PromotionRepository;
import com.skylanka.air.ticket.service.TicketService;
import com.skylanka.air.user.entity.User;
import com.skylanka.air.user.repository.UserRepository;
import com.skylanka.air.shared.event.DomainEvent;
import com.skylanka.air.shared.event.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final UserRepository users;
    private final FlightRepository flights;
    private final SeatRepository seats;
    private final PromotionRepository promotions;
    private final PaymentRepository payments;
    private final TicketService tickets;
    private final DomainEventPublisher events;
    private final RefundPolicyService refundPolicies;

    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");
    private static final BigDecimal SERVICE_FEE_RATE = new BigDecimal("0.05");
    private static final BigDecimal PERCENT = new BigDecimal("100");

    @org.springframework.beans.factory.annotation.Value("${booking.hold-minutes:15}")
    private int holdMinutes;

    private com.skylanka.air.shared.service.SettingsService settings;
    private com.skylanka.air.user.service.LoyaltyService loyalty;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSettings(com.skylanka.air.shared.service.SettingsService settings) {
        this.settings = settings;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLoyalty(com.skylanka.air.user.service.LoyaltyService loyalty) {
        this.loyalty = loyalty;
    }

    private int currentHoldMinutes() {
        return settings == null
                ? holdMinutes
                : settings.getInt(com.skylanka.air.shared.service.SettingsService.BOOKING_HOLD_MINUTES, holdMinutes);
    }

    private BigDecimal taxRate() {
        return settings == null
                ? TAX_RATE
                : settings.getDecimal(com.skylanka.air.shared.service.SettingsService.TAX_PERCENT, TAX_RATE.multiply(PERCENT))
                .divide(PERCENT, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal serviceFeeRate() {
        return settings == null
                ? SERVICE_FEE_RATE
                : settings.getDecimal(com.skylanka.air.shared.service.SettingsService.SERVICE_FEE_PERCENT, SERVICE_FEE_RATE.multiply(PERCENT))
                .divide(PERCENT, 4, RoundingMode.HALF_UP);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BookingService(
            BookingRepository b,
            UserRepository u,
            FlightRepository f,
            SeatRepository s,
            PromotionRepository p,
            PaymentRepository payments,
            TicketService tickets,
            DomainEventPublisher events,
            RefundPolicyService refundPolicies) {

        bookings = b;
        users = u;
        flights = f;
        seats = s;
        promotions = p;
        this.payments = payments;
        this.tickets = tickets;
        this.events = events;
        this.refundPolicies = refundPolicies;
    }

    @Transactional
    public Booking create(
            Long uid,
            Long fid,
            Long sid,
            String name,
            String passport,
            String contact,
            String promoCode) {

        User u = users.findById(uid).orElseThrow();
        Flight f = flights.findById(fid).orElseThrow();

        Seat seat = seats.findByIdForUpdate(sid)
                .orElseThrow();

        if (!seat.getFlight().getId().equals(fid))
            throw new IllegalArgumentException(
                    "Invalid seat for this flight."
            );

        if (seat.getStatus() != SeatStatus.AVAILABLE)
            throw new IllegalStateException(
                    "That seat is no longer available."
            );

        validatePassengerFields(name, passport, contact, f.isInternational());

        if (f.getStatus() == FlightStatus.CANCELLED ||
                f.getStatus() == FlightStatus.COMPLETED ||
                hasDeparted(f)) {

            throw new IllegalStateException(
                    "This flight cannot be booked."
            );
        }

        Booking b = new Booking();

        b.setReference(generateReference());
        b.setCustomer(u);
        b.setFlight(f);
        b.setSeat(seat);

        b.setPassengerName(name.trim());
        b.setPassportNumber(clean(passport));
        b.setPassengerContact(contact.trim());

        b.setSeatClass(seat.getSeatClass());

        BigDecimal base = money(f.getBaseFare());
        BigDecimal tax = base.multiply(taxRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = base.multiply(serviceFeeRate()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotal = base.add(tax).add(fee);
        BigDecimal discount = resolveDiscount(subtotal, promoCode);

        b.setBaseFare(base);
        b.setTax(tax);
        b.setServiceFee(fee);
        b.setDiscount(money(discount));
        b.setPromoCode(promoCode != null && !promoCode.isBlank() ? promoCode.trim().toUpperCase() : null);

        b.setTotalFare(
                money(subtotal.subtract(discount))
        );

        b.setStatus(BookingStatus.PENDING);

        seat.setStatus(SeatStatus.BOOKED);
        seats.save(seat);

        Passenger passenger = new Passenger();
        passenger.setBooking(b);
        passenger.setSeat(seat);
        passenger.setName(name.trim());
        passenger.setPassportNumber(clean(passport));
        passenger.setContact(contact.trim());
        b.getPassengers().add(passenger);

        Booking saved = bookings.save(b);

        announce(DomainEvent.Type.BOOKING_CREATED, b, u, "Booking Created",
                "Booking " +
                        saved.getReference() +
                        " has been created and is awaiting payment.");

        return saved;
    }

    @Transactional
    public Booking createGroup(
            Long uid,
            Long fid,
            List<Long> seatIds,
            List<String> names,
            List<String> passports,
            List<String> contacts,
            String promoCode) {
        return createGroup(uid, fid, seatIds, names, passports, contacts, promoCode, null);
    }

    /**
     * @param holder identity that may have temporarily held some of these seats via the seat map
     *               ("U&lt;userId&gt;" or "S&lt;sessionId&gt;"); seats HELD by this holder are accepted just
     *               like AVAILABLE ones. May be null.
     */
    @Transactional
    public Booking createGroup(
            Long uid,
            Long fid,
            List<Long> seatIds,
            List<String> names,
            List<String> passports,
            List<String> contacts,
            String promoCode,
            String holder) {

        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one seat.");
        }
        // A single blank passport box is submitted as "" and Spring binds that to an EMPTY list
        // (not [""]). Treat "none sent" as one blank passport per seat; validatePassengerFields
        // still rejects blanks on international flights.
        if (passports == null || passports.isEmpty()) {
            passports = new ArrayList<>(Collections.nCopies(seatIds.size(), ""));
        }
        if (names == null || passports == null || contacts == null
                || names.size() != seatIds.size()
                || passports.size() != seatIds.size()
                || contacts.size() != seatIds.size()) {
            throw new IllegalArgumentException("Passenger details must be provided for every selected seat.");
        }
        if (seatIds.size() > com.skylanka.air.seat.service.SeatService.MAX_SEATS_PER_HOLDER) {
            throw new IllegalArgumentException("A single booking can include at most "
                    + com.skylanka.air.seat.service.SeatService.MAX_SEATS_PER_HOLDER + " passengers.");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new IllegalArgumentException("Each passenger must be assigned a different seat.");
        }

        User u = users.findById(uid).orElseThrow();
        Flight f = flights.findById(fid).orElseThrow();

        if (f.getStatus() == FlightStatus.CANCELLED ||
                f.getStatus() == FlightStatus.COMPLETED ||
                hasDeparted(f)) {

            throw new IllegalStateException("This flight cannot be booked.");
        }

        List<Long> lockOrder = new ArrayList<>(seatIds);
        Collections.sort(lockOrder);

        Map<Long, Seat> seatsById = new HashMap<>();
        for (Long sid : lockOrder) {
            Seat seat = seats.findByIdForUpdate(sid).orElseThrow(() ->
                    new IllegalArgumentException("Seat not found.")
            );
            if (!seat.getFlight().getId().equals(fid)) {
                throw new IllegalArgumentException("Invalid seat for this flight.");
            }
            if (!isBookableBy(seat, holder)) {
                throw new IllegalStateException(
                        "Seat " + seat.getSeatNumber() + " is no longer available."
                );
            }
            seatsById.put(sid, seat);
        }

        List<Seat> lockedSeats = new ArrayList<>();
        for (Long sid : seatIds) {
            lockedSeats.add(seatsById.get(sid));
        }

        for (int i = 0; i < names.size(); i++) {
            validatePassengerFields(names.get(i), passports.get(i), contacts.get(i), f.isInternational());
        }

        Booking b = new Booking();
        b.setReference(generateReference());
        b.setCustomer(u);
        b.setFlight(f);

        Seat primarySeat = lockedSeats.get(0);
        b.setSeat(primarySeat);
        b.setSeatClass(primarySeat.getSeatClass());
        b.setPassengerName(names.get(0).trim());
        b.setPassportNumber(clean(passports.get(0)));
        b.setPassengerContact(contacts.get(0).trim());

        int passengerCount = lockedSeats.size();
        BigDecimal base = money(f.getBaseFare())
                .multiply(BigDecimal.valueOf(passengerCount))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = base.multiply(taxRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = base.multiply(serviceFeeRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = base.add(tax).add(fee);
        BigDecimal discount = resolveDiscount(subtotal, promoCode);

        b.setBaseFare(base);
        b.setTax(tax);
        b.setServiceFee(fee);
        b.setDiscount(money(discount));
        b.setPromoCode(promoCode != null && !promoCode.isBlank() ? promoCode.trim().toUpperCase() : null);
        b.setTotalFare(money(subtotal.subtract(discount)));
        b.setStatus(BookingStatus.PENDING);

        for (int i = 0; i < lockedSeats.size(); i++) {
            Seat seat = lockedSeats.get(i);
            seat.setStatus(SeatStatus.BOOKED);
            seat.setHeldBy(null);
            seat.setHeldAt(null);
            seats.save(seat);

            Passenger p = new Passenger();
            p.setBooking(b);
            p.setSeat(seat);
            p.setName(names.get(i).trim());
            p.setPassportNumber(clean(passports.get(i)));
            p.setContact(contacts.get(i).trim());
            b.getPassengers().add(p);
        }

        Booking saved = bookings.save(b);

        announce(DomainEvent.Type.BOOKING_CREATED, b, u, "Booking Created",
                "Booking " + saved.getReference() +
                        " for " + passengerCount + " passenger(s) has been created and is awaiting payment.");

        return saved;
    }

    private BigDecimal resolveDiscount(BigDecimal subtotal, String promoCode) {

        if (promoCode == null || promoCode.isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }

        Promotion promotion = promotions
                .findByCode(promoCode.trim().toUpperCase())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid promotion code."
                        )
                );

        LocalDate today = LocalDate.now();

        if (!promotion.isActive())
            throw new IllegalArgumentException(
                    "This promotion is not active."
            );

        if (promotion.getValidFrom() != null &&
                today.isBefore(promotion.getValidFrom())) {

            throw new IllegalArgumentException(
                    "This promotion is not active yet."
            );
        }

        if (promotion.getValidUntil() != null &&
                today.isAfter(promotion.getValidUntil())) {

            throw new IllegalArgumentException(
                    "This promotion has expired."
            );
        }

        return subtotal
                .multiply(BigDecimal.valueOf(promotion.getPercentage()))
                .divide(PERCENT, 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void cancel(Booking b) {

        if (b.getStatus() == BookingStatus.CANCELLED) return;

        if (b.getStatus() == BookingStatus.COMPLETED)
            throw new IllegalStateException(
                    "Completed bookings cannot be cancelled."
            );

        if (hasDeparted(b.getFlight())) {
            throw new IllegalStateException(
                    "Bookings cannot be cancelled after the flight has departed."
            );
        }

        b.setStatus(BookingStatus.CANCELLED);
        b.setModifiedAt(LocalDateTime.now());

        applyCancellationPayment(b, RefundPolicyService.CancellationReason.CUSTOMER_REQUEST);

        releaseSeats(b);

        bookings.save(b);

        notifyCancellation(b, "Booking " + b.getReference() + " has been cancelled.");
    }

    @Transactional
    public void cancelForFlight(Booking b) {
        if (b.getStatus() == BookingStatus.CANCELLED
                || b.getStatus() == BookingStatus.COMPLETED) {
            return;
        }

        b.setStatus(BookingStatus.CANCELLED);
        b.setModifiedAt(LocalDateTime.now());

        applyCancellationPayment(b, RefundPolicyService.CancellationReason.FLIGHT_CANCELLED);

        releaseSeats(b);

        bookings.save(b);

        notifyCancellation(
                b,
                "Booking " + b.getReference() + " was cancelled because its flight was cancelled."
        );
    }

    @Transactional
    public void notifyFlightDelay(Flight f) {
        List<Booking> affected = bookings.findByFlightId(f.getId()).stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING
                        || b.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        int hours = (f.getDelayMinutes() == null ? 0 : f.getDelayMinutes()) / 60;
        int minutes = (f.getDelayMinutes() == null ? 0 : f.getDelayMinutes()) % 60;
        String delayPhrase = hours > 0
                ? hours + "h" + (minutes > 0 ? " " + minutes + "m" : "")
                : minutes + " minutes";

        for (Booking b : affected) {
            if (b.getCustomer() == null) continue;

            RefundPolicyService.RefundDecision quote =
                    refundPolicies.determine(b, RefundPolicyService.CancellationReason.CUSTOMER_REQUEST);

            StringBuilder msg = new StringBuilder(
                    "Flight " + f.getFlightNumber() + " for booking " + b.getReference() +
                            " is now delayed by " + delayPhrase + "."
            );

            if (quote.refundable()) {
                msg.append(" If you choose not to travel, you're eligible for a refund of LKR ")
                        .append(quote.amount())
                        .append(" (").append(quote.explanation()).append(")");
            }

            announce(DomainEvent.Type.FLIGHT_DELAYED, b, b.getCustomer(), "Flight Delayed", msg.toString());
        }
    }

    private void notifyCancellation(Booking b, String message) {
        if (b.getCustomer() == null) return;

        announce(DomainEvent.Type.BOOKING_CANCELLED, b, b.getCustomer(), "Booking Cancelled", message);
    }

    /** Observer pattern: tell every registered observer (in-app, email, log...) that something happened. */
    private void announce(DomainEvent.Type type, Booking booking, User recipient, String title, String message) {
        events.publish(new DomainEvent(type, booking, recipient, title, message));
    }

    @Transactional
    public int expirePendingBookings() {

        LocalDateTime cutoff =
                LocalDateTime.now().minusMinutes(currentHoldMinutes());

        List<Booking> expired =
                bookings.findByStatusAndCreatedAtBefore(
                        BookingStatus.PENDING,
                        cutoff
                );

        for (Booking b : expired) {

            b.setStatus(BookingStatus.CANCELLED);
            b.setModifiedAt(LocalDateTime.now());

            releaseSeats(b);

            bookings.save(b);

            if (b.getCustomer() != null) {

                announce(DomainEvent.Type.BOOKING_EXPIRED, b, b.getCustomer(), "Booking Expired",
                        "Booking " +
                                b.getReference() +
                                " expired because payment was not completed within " + currentHoldMinutes() + " minutes.");
            }
        }

        return expired.size();
    }

    @Transactional
    public void modify(
            Booking b,
            String passengerName,
            String passportNumber,
            String passengerContact) {

        if (b.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cancelled bookings cannot be modified."
            );
        }

        if (b.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed bookings cannot be modified."
            );
        }

        if (hasDeparted(b.getFlight())) {
            throw new IllegalStateException(
                    "Bookings for flights that have already departed cannot be modified."
            );
        }

        validatePassengerFields(passengerName, passportNumber, passengerContact, b.getFlight().isInternational());

        b.setPassengerName(passengerName.trim());
        b.setPassportNumber(clean(passportNumber));
        b.setPassengerContact(passengerContact.trim());
        b.setModifiedAt(LocalDateTime.now());

        if (b.getPassengers() != null && !b.getPassengers().isEmpty()) {
            Passenger primary = b.getPassengers().get(0);
            primary.setName(passengerName.trim());
            primary.setPassportNumber(clean(passportNumber));
            primary.setContact(passengerContact.trim());
        }

        bookings.save(b);

        if (b.getStatus() == BookingStatus.CONFIRMED && b.getPassengers() != null && !b.getPassengers().isEmpty()) {
            tickets.reissue(b.getPassengers().get(0));
        }

        if (b.getCustomer() != null) {

            announce(DomainEvent.Type.BOOKING_MODIFIED, b, b.getCustomer(), "Booking Modified",
                    "Booking " +
                            b.getReference() +
                            " has been successfully modified.");
        }
    }

    @Transactional
    public void modifyPassenger(
            Booking b,
            Long passengerId,
            String name,
            String passportNumber,
            String contact) {

        if (b.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cancelled bookings cannot be modified."
            );
        }

        if (b.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed bookings cannot be modified."
            );
        }

        if (hasDeparted(b.getFlight())) {
            throw new IllegalStateException(
                    "Bookings for flights that have already departed cannot be modified."
            );
        }

        Passenger target = b.getPassengers().stream()
                .filter(p -> p.getId().equals(passengerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "That passenger is not part of this booking."
                ));

        validatePassengerFields(name, passportNumber, contact, b.getFlight().isInternational());

        target.setName(name.trim());
        target.setPassportNumber(clean(passportNumber));
        target.setContact(contact.trim());

        if (b.getPassengers().indexOf(target) == 0) {
            b.setPassengerName(name.trim());
            b.setPassportNumber(clean(passportNumber));
            b.setPassengerContact(contact.trim());
        }

        b.setModifiedAt(LocalDateTime.now());
        bookings.save(b);

        if (b.getStatus() == BookingStatus.CONFIRMED) {
            tickets.reissue(target);
        }

        if (b.getCustomer() != null) {
            announce(DomainEvent.Type.BOOKING_MODIFIED, b, b.getCustomer(), "Booking Modified",
                    "Passenger details for " + target.getName() +
                            " on booking " + b.getReference() + " have been updated.");
        }
    }

    /** AVAILABLE, or HELD by this holder, or HELD by someone else but the hold has lapsed. */
    private boolean isBookableBy(Seat seat, String holder) {
        if (seat.getStatus() == SeatStatus.AVAILABLE) return true;
        if (seat.getStatus() != SeatStatus.HELD || seat.getHeldBy() == null) return false;
        if (holder != null && holder.equals(seat.getHeldBy())) return true;
        return seat.getHeldAt() != null
                && seat.getHeldAt().isBefore(LocalDateTime.now().minusMinutes(
                settings == null
                        ? 10
                        : settings.getInt(com.skylanka.air.shared.service.SettingsService.SEAT_HOLD_MINUTES, 10)));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("[\\p{L} .'-]{2,100}");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("[A-Za-z0-9]{6,9}");
    private static final Pattern CONTACT_PATTERN = Pattern.compile("\\+?[0-9]{7,15}");

    private void validatePassengerFields(String name, String passport, String contact, boolean passportRequired) {
        if (name == null || !NAME_PATTERN.matcher(name.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Enter a valid passenger name (2-100 letters, spaces, apostrophes or hyphens)."
            );
        }
        boolean passportBlank = passport == null || passport.isBlank();
        if (passportBlank ? passportRequired : !PASSPORT_PATTERN.matcher(passport.trim()).matches()) {
            throw new IllegalArgumentException(
                    passportRequired
                            ? "Enter a valid passport number (6-9 letters/digits) - required for international flights."
                            : "Enter a valid passport number (6-9 letters/digits) or leave it blank for this domestic flight."
            );
        }
        if (contact == null || !CONTACT_PATTERN.matcher(contact.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Enter a valid contact number (7-15 digits, optional leading +)."
            );
        }
    }

    private String generateReference() {

        String r;

        do {
            r = "SKY-" +
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 8)
                            .toUpperCase();

        } while (bookings.findByReference(r).isPresent());

        return r;
    }

    private void applyCancellationPayment(
            Booking booking,
            RefundPolicyService.CancellationReason reason) {
        payments.findByBookingId(booking.getId()).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.PAID
                    && payment.getStatus() != PaymentStatus.VERIFIED) {
                return;
            }

            RefundPolicyService.RefundDecision decision =
                    refundPolicies.determine(booking, reason);
            payment.setRefundAmount(decision.amount());
            payment.setStatus(
                    decision.refundable()
                            ? PaymentStatus.REFUNDED
                            : PaymentStatus.VOID
            );
            payment.setUpdatedAt(LocalDateTime.now());
            payments.save(payment);
            tickets.voidTicket(booking);
            if (loyalty != null) {
                loyalty.revoke(booking.getCustomer(), payment.getAmount());
            }
        });
    }

    private void releaseSeats(Booking b) {
        List<Seat> toRelease = new ArrayList<>();

        if (b.getPassengers() != null && !b.getPassengers().isEmpty()) {
            for (Passenger p : b.getPassengers()) {
                if (p.getSeat() != null) {
                    toRelease.add(p.getSeat());
                }
            }
        } else if (b.getSeat() != null) {
            toRelease.add(b.getSeat());
        }

        for (Seat seat : toRelease) {
            if (seat.getStatus() != SeatStatus.BLOCKED) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldBy(null);
                seat.setHeldAt(null);
                seats.save(seat);
            }
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasDeparted(Flight flight) {
        return LocalDateTime.of(
                flight.getDepartureDate(),
                flight.getDepartureTime()
        ).isBefore(LocalDateTime.now());
    }
}