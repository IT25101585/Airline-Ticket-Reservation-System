package com.skylanka.ticketmanagement.entity;

import com.skylanka.ticketmanagement.entity.stubs.*;
import com.skylanka.ticketmanagement.enums.TicketStatus;
import com.skylanka.ticketmanagement.enums.TicketType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core Ticket entity — owned by the Ticket Management module.
 *
 * Status lifecycle:
 *   ACTIVE → REISSUED | VOID | CANCELLED
 *   REISSUED → VOID
 *   VOID / CANCELLED → (terminal, no further transitions)
 */
@Getter
@Setter
@Entity
@Table(
    name = "tickets",
    indexes = {
        @Index(name = "idx_ticket_booking",  columnList = "booking_id"),
        @Index(name = "idx_ticket_user",     columnList = "user_id"),
        @Index(name = "idx_ticket_status",   columnList = "ticket_status"),
        @Index(name = "idx_ticket_qr_token", columnList = "qr_code_token")
    }
)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Human-readable ticket number. Format: SKL-YYYYMMDD-NNNNNN
     * Guaranteed unique via DB UNIQUE constraint.
     */
    @Column(name = "ticket_number", nullable = false, unique = true, length = 25)
    private String ticketNumber;

    /** Version increments on each reissue. Starts at 1. */
    @Column(name = "ticket_version", nullable = false)
    private int ticketVersion = 1;

    // ===== Linked Entities =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // ===== Status =====

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status", nullable = false, length = 20)
    private TicketStatus ticketStatus = TicketStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false, length = 30)
    private TicketType ticketType = TicketType.E_TICKET;

    // ===== QR / Barcode =====

    /**
     * Cryptographically secure random token stored in the QR code.
     * Never contains PII or payment data directly.
     */
    @Column(name = "qr_code_token", nullable = false, unique = true, length = 255)
    private String qrCodeToken;

    /** Base64-encoded PNG of the QR code image. */
    @Lob
    @Column(name = "qr_code_data", columnDefinition = "NVARCHAR(MAX)")
    private String qrCodeData;

    // ===== Validity =====

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    // ===== Versioning / Reissue =====

    /** Self-referential FK: points to the ticket this one was reissued from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reissued_from_ticket_id")
    private Ticket reissuedFromTicket;

    @Column(name = "reissue_reason", length = 500)
    private String reissueReason;

    // ===== Void / Cancel =====

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // ===== Audit =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt  = LocalDateTime.now();
        if (issuedAt == null) issuedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
