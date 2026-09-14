package com.skylanka.ticketmanagement.service;

import com.skylanka.ticketmanagement.entity.AuditLog;
import com.skylanka.ticketmanagement.enums.AuditAction;
import com.skylanka.ticketmanagement.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Append-only audit logging service.
 * All writes are performed asynchronously so they don't block the
 * main request thread. The calling business operation is NOT rolled
 * back if the audit write fails — it is logged at ERROR level.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record an audit event.
     *
     * @param action    the type of action performed
     * @param ticketId  the ticket involved (may be null for pre-creation events)
     * @param userId    the user who performed the action
     * @param ipAddress requester's IP address
     * @param metadata  free-form JSON string for extra context
     */
    @Async
    public void log(AuditAction action,
                    UUID ticketId,
                    UUID userId,
                    String ipAddress,
                    String metadata) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setTicketId(ticketId);
            entry.setUserId(userId);
            entry.setIpAddress(ipAddress);
            entry.setMetadata(metadata);
            auditLogRepository.save(entry);

            log.info("AUDIT [{}] ticket={} user={}", action, ticketId, userId);
        } catch (Exception ex) {
            // Audit failure must never crash the main operation
            log.error("Failed to write audit log for action {}: {}", action, ex.getMessage(), ex);
        }
    }
}
