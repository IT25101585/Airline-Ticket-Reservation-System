package com.skylanka.ticketmanagement.entity;

import com.skylanka.ticketmanagement.enums.AuditAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit log for all significant ticket operations.
 * Never modified after creation.
 */
@Getter
@Setter
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_al_ticket_id",  columnList = "ticket_id"),
        @Index(name = "idx_al_user_id",    columnList = "user_id"),
        @Index(name = "idx_al_created_at", columnList = "created_at")
    }
)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** JSON string with additional context. */
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
