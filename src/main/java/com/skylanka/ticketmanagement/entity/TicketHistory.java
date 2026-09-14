package com.skylanka.ticketmanagement.entity;

import com.skylanka.ticketmanagement.entity.stubs.User;
import com.skylanka.ticketmanagement.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable snapshot of a ticket's state at every status-change event.
 * Records are append-only — never updated after insert.
 */
@Getter
@Setter
@Entity
@Table(
    name = "ticket_histories",
    indexes = {
        @Index(name = "idx_th_ticket_id", columnList = "ticket_id")
    }
)
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private TicketStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private TicketStatus newStatus;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "ticket_version_at_change")
    private int ticketVersionAtChange;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
