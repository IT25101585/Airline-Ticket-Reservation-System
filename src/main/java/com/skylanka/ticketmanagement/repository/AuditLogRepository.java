package com.skylanka.ticketmanagement.repository;

import com.skylanka.ticketmanagement.entity.AuditLog;
import com.skylanka.ticketmanagement.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access layer for AuditLog (append-only).
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);
}
