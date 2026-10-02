package com.skylanka.air.shared.repository;

import com.skylanka.air.shared.entity.AuditLog;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    /** Newest entries for the admin dashboard preview. */
    List<AuditLog> findTop5ByOrderByCreatedAtDesc();

    /** Whole audit log, newest first, one page at a time (admin "full audit log" page). */
    org.springframework.data.domain.Page<AuditLog> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select count(a) from AuditLog a where a.userId = :uid "
            + "and a.action not in ('HTTP_REQUEST', 'FLAGGED_ACTIVITY') and a.createdAt >= :since")
    long countBusinessActionsSince(@org.springframework.data.repository.query.Param("uid") Long uid,
                                   @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    long countByAction(String action);
}
