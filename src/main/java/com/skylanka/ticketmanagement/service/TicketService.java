package com.skylanka.ticketmanagement.service;

import com.skylanka.ticketmanagement.dto.request.GenerateTicketRequest;
import com.skylanka.ticketmanagement.dto.request.ReissueTicketRequest;
import com.skylanka.ticketmanagement.dto.request.ValidateTicketRequest;
import com.skylanka.ticketmanagement.dto.request.VoidTicketRequest;
import com.skylanka.ticketmanagement.dto.response.TicketResponse;
import com.skylanka.ticketmanagement.dto.response.TicketValidationResponse;
import com.skylanka.ticketmanagement.entity.Ticket;
import com.skylanka.ticketmanagement.entity.TicketHistory;
import com.skylanka.ticketmanagement.entity.stubs.Booking;
import com.skylanka.ticketmanagement.entity.stubs.Payment;
import com.skylanka.ticketmanagement.entity.stubs.User;
import com.skylanka.ticketmanagement.enums.*;
import com.skylanka.ticketmanagement.exception.AppException;
import com.skylanka.ticketmanagement.repository.*;
import com.skylanka.ticketmanagement.security.UserPrincipal;
import com.skylanka.ticketmanagement.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Core Ticket Management business logic.
 *
 * All state-changing methods run inside a database transaction.
 * Pessimistic locking is used during generation and reissue to prevent
 * duplicate tickets from concurrent requests.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository        ticketRepo;
    private final TicketHistoryRepository historyRepo;
    private final BookingRepository       bookingRepo;
    private final PaymentRepository       paymentRepo;
    private final TicketNumberService     numberService;
    private final QRCodeService           qrService;
    private final AuditLogService         auditService;
    private final NotificationService     notifyService;

    public TicketService(TicketRepository ticketRepo,
                         TicketHistoryRepository historyRepo,
                         BookingRepository bookingRepo,
                         PaymentRepository paymentRepo,
                         TicketNumberService numberService,
                         QRCodeService qrService,
                         AuditLogService auditService,
                         NotificationService notifyService) {
        this.ticketRepo    = ticketRepo;
        this.historyRepo   = historyRepo;
        this.bookingRepo   = bookingRepo;
        this.paymentRepo   = paymentRepo;
        this.numberService = numberService;
        this.qrService     = qrService;
        this.auditService  = auditService;
        this.notifyService = notifyService;
    }

    // =========================================================================
    // 1. Generate Ticket
    // =========================================================================

    /**
     * Generate a ticket after a booking's payment is confirmed as PAID.
     *
     * Business rules enforced:
     *   - Booking must exist.
     *   - Booking must NOT be CANCELLED.
     *   - A PAID payment must exist for this booking.
     *   - An ACTIVE ticket must NOT already exist for this booking.
     *
     * Uses pessimistic locking to prevent concurrent duplicates.
     */
    @Transactional
    public TicketResponse generateTicket(GenerateTicketRequest request, String ipAddress) {
        UUID bookingId = UUID.fromString(request.getBookingId());
        UserPrincipal actor = SecurityUtils.getCurrentUser();

        // ── 1. Load & validate booking ────────────────────────────────────────
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> AppException.bookingNotFound(request.getBookingId()));

        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw AppException.bookingCancelled();
        }

        // ── 2. Verify payment is PAID ─────────────────────────────────────────
        Payment payment = paymentRepo
                .findLatestByBookingIdAndStatus(bookingId, PaymentStatus.PAID)
                .orElseThrow(AppException::paymentNotCompleted);

        // ── 3. Duplicate check (with pessimistic lock) ────────────────────────
        ticketRepo.findActiveByBookingIdForUpdate(bookingId).ifPresent(existing -> {
            throw AppException.ticketAlreadyExists(request.getBookingId());
        });

        // ── 4. Create ticket ──────────────────────────────────────────────────
        String qrToken  = qrService.generateUniqueToken();
        String qrBase64 = qrService.generateQRCodeBase64(qrToken);

        Ticket ticket = new Ticket();
        ticket.setTicketNumber(numberService.generateUniqueTicketNumber());
        ticket.setTicketVersion(1);
        ticket.setBooking(booking);
        ticket.setPassenger(booking.getPassenger());
        ticket.setUser(booking.getUser());
        ticket.setFlight(booking.getFlight());
        ticket.setSeat(booking.getSeat());
        ticket.setPayment(payment);
        ticket.setTicketStatus(TicketStatus.ACTIVE);
        ticket.setTicketType(TicketType.E_TICKET);
        ticket.setQrCodeToken(qrToken);
        ticket.setQrCodeData(qrBase64);
        ticket.setIssuedAt(LocalDateTime.now());
        ticket.setValidFrom(LocalDateTime.now());
        ticket.setValidUntil(booking.getFlight().getDepartureTime().plusHours(24));

        if (actor != null) {
            User issuer = new User();
            issuer.setId(actor.getId());
            ticket.setIssuedBy(issuer);
        }

        Ticket saved = ticketRepo.save(ticket);

        // ── 5. Record history snapshot ────────────────────────────────────────
        saveHistory(saved, null, TicketStatus.ACTIVE, "Initial ticket generation", actor);

        // ── 6. Audit + Notify (async) ─────────────────────────────────────────
        auditService.log(AuditAction.TICKET_GENERATED, saved.getId(),
                actor != null ? actor.getId() : null, ipAddress,
                "{\"bookingId\":\"" + bookingId + "\",\"ticketNumber\":\"" + saved.getTicketNumber() + "\"}");
        notifyService.onTicketGenerated(saved);

        log.info("Ticket generated: {} for booking: {}", saved.getTicketNumber(), bookingId);
        return toResponse(saved, true);
    }

    /**
     * Integration hook called by the Payment module when a payment is confirmed.
     * Wraps generateTicket() with a synthetic request.
     */
    @Transactional
    public void generateAfterPayment(UUID bookingId) {
        GenerateTicketRequest req = new GenerateTicketRequest();
        req.setBookingId(bookingId.toString());
        generateTicket(req, "SYSTEM");
    }

    // =========================================================================
    // 2. Get Ticket by ID
    // =========================================================================

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID ticketId, String ipAddress) {
        Ticket ticket = findTicketOrThrow(ticketId);
        UserPrincipal actor = SecurityUtils.getCurrentUser();

        // IDOR prevention: customers can only see their own tickets
        enforceOwnership(ticket, actor);

        auditService.log(AuditAction.TICKET_VIEWED, ticketId,
                actor != null ? actor.getId() : null, ipAddress, null);

        return toResponse(ticket, true);
    }

    // =========================================================================
    // 3. Get Ticket(s) by Booking
    // =========================================================================

    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByBooking(UUID bookingId) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        List<Ticket> tickets = ticketRepo.findAllByBookingIdOrderByVersionDesc(bookingId);

        // Customers may only see their own booking's tickets
        if (actor != null && actor.getRole() == UserRole.CUSTOMER) {
            tickets = tickets.stream()
                    .filter(t -> t.getUser().getId().equals(actor.getId()))
                    .toList();
        }
        return tickets.stream().map(t -> toResponse(t, false)).toList();
    }

    // =========================================================================
    // 4. Get My Tickets (Customer)
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<TicketResponse> getMyTickets(TicketStatus status, int page, int size) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        if (actor == null) throw AppException.unauthorizedAccess();

        Pageable pageable = PageRequest.of(page, size, Sort.by("issuedAt").descending());
        Page<Ticket> result = ticketRepo.findByUserId(actor.getId(), status, pageable);
        return result.map(t -> toResponse(t, false));
    }

    // =========================================================================
    // 5. Validate Ticket (QR code scan)
    // =========================================================================

    @Transactional(readOnly = true)
    public TicketValidationResponse validateTicket(ValidateTicketRequest request,
                                                   String ipAddress) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();

        Ticket ticket = ticketRepo.findByQrCodeToken(request.getQrCodeToken())
                .orElse(null);

        if (ticket == null) {
            auditService.log(AuditAction.TICKET_VALIDATED, null,
                    actor != null ? actor.getId() : null, ipAddress,
                    "{\"result\":\"INVALID\",\"reason\":\"TOKEN_NOT_FOUND\"}");
            return TicketValidationResponse.invalid("QR token not found.");
        }

        auditService.log(AuditAction.TICKET_VALIDATED, ticket.getId(),
                actor != null ? actor.getId() : null, ipAddress,
                "{\"result\":\"" + (ticket.getTicketStatus() == TicketStatus.ACTIVE ? "VALID" : "INVALID") + "\"}");

        if (ticket.getTicketStatus() != TicketStatus.ACTIVE) {
            TicketValidationResponse r = TicketValidationResponse.invalid(
                    "Ticket is " + ticket.getTicketStatus() + " and cannot be used.");
            r.setTicketStatus(ticket.getTicketStatus());
            r.setTicketNumber(ticket.getTicketNumber());
            return r;
        }

        TicketValidationResponse r = new TicketValidationResponse();
        r.setValid(true);
        r.setTicketNumber(ticket.getTicketNumber());
        r.setPassengerName(ticket.getPassenger().getFullName());
        r.setFlightNumber(ticket.getFlight().getFlightNumber());
        r.setOrigin(ticket.getFlight().getOriginCode());
        r.setDestination(ticket.getFlight().getDestinationCode());
        r.setDepartureTime(ticket.getFlight().getDepartureTime());
        r.setSeatNumber(ticket.getSeat().getSeatNumber());
        r.setCabinClass(ticket.getSeat().getCabinClass());
        r.setBookingReference(ticket.getBooking().getBookingReference());
        r.setTicketStatus(ticket.getTicketStatus());
        return r;
    }

    // =========================================================================
    // 6. Reissue Ticket
    // =========================================================================

    /**
     * Reissue a ticket (e.g., after a booking modification).
     *
     * Steps:
     *   a) Validate old ticket is ACTIVE.
     *   b) Mark old ticket REISSUED — snapshot to history.
     *   c) Create new ticket with incremented version + new QR token.
     *   d) Audit + notify.
     */
    @Transactional
    public TicketResponse reissueTicket(UUID ticketId,
                                        ReissueTicketRequest request,
                                        String ipAddress) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Ticket oldTicket = findTicketOrThrow(ticketId);

        // Validate transition ACTIVE → REISSUED
        if (!oldTicket.getTicketStatus().canTransitionTo(TicketStatus.REISSUED)) {
            throw AppException.invalidStatusTransition(
                    oldTicket.getTicketStatus().name(), "REISSUED");
        }

        // Mark old ticket as REISSUED
        TicketStatus prevStatus = oldTicket.getTicketStatus();
        oldTicket.setTicketStatus(TicketStatus.REISSUED);
        oldTicket.setReissueReason(request.getReason());
        if (actor != null) {
            User u = new User(); u.setId(actor.getId());
            oldTicket.setUpdatedBy(u);
        }
        ticketRepo.save(oldTicket);
        saveHistory(oldTicket, prevStatus, TicketStatus.REISSUED, request.getReason(), actor);

        // Create new ticket version
        String newQrToken  = qrService.generateUniqueToken();
        String newQrBase64 = qrService.generateQRCodeBase64(newQrToken);

        Ticket newTicket = new Ticket();
        newTicket.setTicketNumber(numberService.generateUniqueTicketNumber());
        newTicket.setTicketVersion(oldTicket.getTicketVersion() + 1);
        newTicket.setBooking(oldTicket.getBooking());
        newTicket.setPassenger(oldTicket.getPassenger());
        newTicket.setUser(oldTicket.getUser());
        newTicket.setFlight(oldTicket.getFlight());
        newTicket.setSeat(oldTicket.getSeat());
        newTicket.setPayment(oldTicket.getPayment());
        newTicket.setTicketStatus(TicketStatus.ACTIVE);
        newTicket.setTicketType(oldTicket.getTicketType());
        newTicket.setQrCodeToken(newQrToken);
        newTicket.setQrCodeData(newQrBase64);
        newTicket.setIssuedAt(LocalDateTime.now());
        newTicket.setValidFrom(LocalDateTime.now());
        newTicket.setValidUntil(oldTicket.getValidUntil());
        newTicket.setReissuedFromTicket(oldTicket);
        newTicket.setReissueReason(request.getReason());
        if (actor != null) {
            User u = new User(); u.setId(actor.getId());
            newTicket.setIssuedBy(u);
        }

        Ticket savedNew = ticketRepo.save(newTicket);
        saveHistory(savedNew, null, TicketStatus.ACTIVE, "Reissued from " + oldTicket.getTicketNumber(), actor);

        auditService.log(AuditAction.TICKET_REISSUED, savedNew.getId(),
                actor != null ? actor.getId() : null, ipAddress,
                "{\"oldTicket\":\"" + oldTicket.getTicketNumber() +
                "\",\"newTicket\":\"" + savedNew.getTicketNumber() +
                "\",\"reason\":\"" + request.getReason() + "\"}");
        notifyService.onTicketReissued(oldTicket, savedNew);

        log.info("Ticket reissued: old={} new={}", oldTicket.getTicketNumber(), savedNew.getTicketNumber());
        return toResponse(savedNew, true);
    }

    /**
     * Integration hook called by the Booking module when a booking is modified.
     */
    @Transactional
    public void reissueOnBookingModification(UUID bookingId, String reason) {
        ticketRepo.findByBookingIdAndStatus(bookingId, TicketStatus.ACTIVE).ifPresent(ticket -> {
            ReissueTicketRequest req = new ReissueTicketRequest();
            req.setReason(reason != null ? reason : "Booking modified");
            reissueTicket(ticket.getId(), req, "SYSTEM");
        });
    }

    // =========================================================================
    // 7. Void Ticket
    // =========================================================================

    @Transactional
    public TicketResponse voidTicket(UUID ticketId, VoidTicketRequest request, String ipAddress) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Ticket ticket = findTicketOrThrow(ticketId);

        if (!ticket.getTicketStatus().canTransitionTo(TicketStatus.VOID)) {
            throw AppException.invalidStatusTransition(ticket.getTicketStatus().name(), "VOID");
        }

        TicketStatus prevStatus = ticket.getTicketStatus();
        ticket.setTicketStatus(TicketStatus.VOID);
        ticket.setVoidReason(request.getReason());
        if (actor != null) {
            User u = new User(); u.setId(actor.getId());
            ticket.setUpdatedBy(u);
        }
        ticketRepo.save(ticket);
        saveHistory(ticket, prevStatus, TicketStatus.VOID, request.getReason(), actor);

        auditService.log(AuditAction.TICKET_VOIDED, ticket.getId(),
                actor != null ? actor.getId() : null, ipAddress,
                "{\"reason\":\"" + request.getReason() + "\"}");
        notifyService.onTicketVoided(ticket, request.getReason());

        log.info("Ticket voided: {}", ticket.getTicketNumber());
        return toResponse(ticket, false);
    }

    /**
     * Integration hook: called by the Booking module when a booking is cancelled.
     */
    @Transactional
    public void voidOnCancellation(UUID bookingId, String reason) {
        ticketRepo.findByBookingIdAndStatus(bookingId, TicketStatus.ACTIVE)
                .ifPresent(ticket -> {
                    TicketStatus prevStatus = ticket.getTicketStatus();
                    ticket.setTicketStatus(TicketStatus.CANCELLED);
                    ticket.setVoidReason(reason != null ? reason : "Booking cancelled");
                    ticket.setCancelledAt(LocalDateTime.now());
                    ticketRepo.save(ticket);
                    saveHistory(ticket, prevStatus, TicketStatus.CANCELLED,
                            "Booking cancelled", null);
                    auditService.log(AuditAction.TICKET_CANCELLED, ticket.getId(),
                            null, "SYSTEM",
                            "{\"reason\":\"" + ticket.getVoidReason() + "\"}");
                    notifyService.onTicketCancelled(ticket, ticket.getVoidReason());
                    log.info("Ticket cancelled due to booking cancellation: {}", ticket.getTicketNumber());
                });
    }

    // =========================================================================
    // 8. PDF Download
    // =========================================================================

    @Transactional(readOnly = true)
    public Ticket getTicketForPdf(UUID ticketId, String ipAddress) {
        Ticket ticket = findTicketOrThrow(ticketId);
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        enforceOwnership(ticket, actor);

        auditService.log(AuditAction.TICKET_DOWNLOADED, ticketId,
                actor != null ? actor.getId() : null, ipAddress, null);

        return ticket;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Ticket findTicketOrThrow(UUID ticketId) {
        return ticketRepo.findById(ticketId)
                .orElseThrow(() -> AppException.ticketNotFound(ticketId.toString()));
    }

    /**
     * IDOR prevention: customers can only access their own tickets.
     * Staff roles (AIRLINE_OPERATIONS_OFFICER, SYSTEM_ADMINISTRATOR) can access all.
     */
    private void enforceOwnership(Ticket ticket, UserPrincipal actor) {
        if (actor == null) throw AppException.unauthorizedAccess();
        if (actor.getRole() == UserRole.CUSTOMER
                && !ticket.getUser().getId().equals(actor.getId())) {
            throw AppException.unauthorizedAccess();
        }
    }

    /** Append an immutable history snapshot for every status change. */
    private void saveHistory(Ticket ticket,
                             TicketStatus previousStatus,
                             TicketStatus newStatus,
                             String reason,
                             UserPrincipal actor) {
        TicketHistory h = new TicketHistory();
        h.setTicket(ticket);
        h.setPreviousStatus(previousStatus);
        h.setNewStatus(newStatus);
        h.setChangeReason(reason);
        h.setTicketVersionAtChange(ticket.getTicketVersion());
        if (actor != null) {
            User u = new User();
            u.setId(actor.getId());
            h.setChangedBy(u);
        }
        historyRepo.save(h);
    }

    // =========================================================================
    // DTO Mapper
    // =========================================================================

    /**
     * Map a Ticket entity to a TicketResponse DTO.
     * No sensitive payment data is included.
     *
     * @param includeQrData if true, includes the Base64 QR PNG (omit in list views)
     */
    public TicketResponse toResponse(Ticket t, boolean includeQrData) {
        TicketResponse r = new TicketResponse();
        r.setId(t.getId());
        r.setTicketNumber(t.getTicketNumber());
        r.setTicketVersion(t.getTicketVersion());
        r.setTicketStatus(t.getTicketStatus());
        r.setTicketType(t.getTicketType());

        if (t.getBooking() != null) {
            r.setBookingId(t.getBooking().getId());
            r.setBookingReference(t.getBooking().getBookingReference());
        }
        if (t.getPassenger() != null) {
            r.setPassengerId(t.getPassenger().getId());
            r.setPassengerName(t.getPassenger().getFullName());
            r.setMaskedPassportNumber(t.getPassenger().getMaskedPassportNumber());
        }
        if (t.getUser() != null) {
            r.setUserId(t.getUser().getId());
        }
        if (t.getFlight() != null) {
            r.setFlightId(t.getFlight().getId());
            r.setFlightNumber(t.getFlight().getFlightNumber());
            r.setOriginCode(t.getFlight().getOriginCode());
            r.setOriginName(t.getFlight().getOriginName());
            r.setDestinationCode(t.getFlight().getDestinationCode());
            r.setDestinationName(t.getFlight().getDestinationName());
            r.setDepartureTime(t.getFlight().getDepartureTime());
            r.setArrivalTime(t.getFlight().getArrivalTime());
        }
        if (t.getSeat() != null) {
            r.setSeatId(t.getSeat().getId());
            r.setSeatNumber(t.getSeat().getSeatNumber());
            r.setCabinClass(t.getSeat().getCabinClass());
        }

        r.setQrCodeToken(t.getQrCodeToken());
        if (includeQrData) r.setQrCodeData(t.getQrCodeData());

        r.setIssuedAt(t.getIssuedAt());
        r.setValidFrom(t.getValidFrom());
        r.setValidUntil(t.getValidUntil());

        if (t.getReissuedFromTicket() != null) {
            r.setReissuedFromTicketId(t.getReissuedFromTicket().getId());
        }
        r.setReissueReason(t.getReissueReason());
        r.setVoidReason(t.getVoidReason());
        r.setCancelledAt(t.getCancelledAt());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        return r;
    }
}
